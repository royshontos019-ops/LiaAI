package com.Lia.assistant.agent.social

import com.Lia.assistant.agent.core.ActionResult
import com.Lia.assistant.agent.core.Blocker
import com.Lia.assistant.agent.core.DriverResult
import com.Lia.assistant.agent.core.FailureReason
import com.Lia.assistant.agent.core.Located
import com.Lia.assistant.agent.core.Resolution
import com.Lia.assistant.agent.core.ScreenDriver
import com.Lia.assistant.agent.core.ScreenObservation
import com.Lia.assistant.agent.core.TargetResolver
import com.Lia.assistant.agent.core.TaskState
import com.Lia.assistant.agent.core.TaskStateMachine
import com.Lia.assistant.agent.core.TextInputExecutor
import com.Lia.assistant.agent.core.UiActionExecutor
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

/** One running or finished social task. Only [SocialTaskRunner] changes it. */
class SocialTask internal constructor(
    val id: String,
    val request: SocialTaskRequest,
    val plan: List<PlanStep>,
) {
    val machine = TaskStateMachine()

    @Volatile var stepIndex = 0
    @Volatile var status = TaskStatus.QUEUED
    @Volatile var code = "queued"
    @Volatile var message = "Waiting to start."
    @Volatile var blocker: Blocker? = null
    @Volatile var failure: FailureReason? = null

    /** True once the task has asked the user. Confirm is only accepted after that. */
    @Volatile var confirmOffered = false

    /** True after the user's yes was accepted. Used up by the one irreversible tap. */
    @Volatile var confirmGranted = false

    @Volatile var cancelRequested = false
    @Volatile var appLaunched = false
    internal val running = AtomicBoolean(false)
}

/** The answer to a control command. [continueRun] says the task should now carry on. */
class ControlOutcome(val report: TaskReport, val task: SocialTask?, val continueRun: Boolean)

/**
 * Runs social tasks step by step and keeps every rule in one place:
 * - PUBLISH asks first. The Share tap only happens after an accepted CONFIRM.
 * - DRAFT has no Publish step, and a Publish step in a DRAFT task is refused.
 * - A blocker (login, captcha...) pauses the task for the user.
 * - If the Share tap cannot be verified, the task ends as SUBMITTED_UNVERIFIED and is never repeated.
 */
class SocialTaskRunner(
    private val driver: ScreenDriver,
    private val executor: UiActionExecutor,
    private val typer: TextInputExecutor,
    private val opener: AppOpener,
    private val detector: BlockerDetector = BlockerDetector(),
    private val planner: SocialPlanner = SocialPlanner(),
    private val pause: suspend (Long) -> Unit = { delay(it) },
    private val newId: () -> String = { UUID.randomUUID().toString().take(8) },
) {
    private val tasks = ConcurrentHashMap<String, SocialTask>()
    private val resolver = TargetResolver()

    fun task(id: String): SocialTask? = tasks[id]

    /** The task that is not finished yet, if any. Only one runs at a time. */
    fun active(): SocialTask? = tasks.values.firstOrNull { !it.status.isFinished }

    /** Makes a task for a checked request, or null while another task is still going. */
    @Synchronized
    fun create(request: SocialTaskRequest): SocialTask? {
        if (active() != null) return null
        val task = SocialTask(newId(), request, planner.plan(request))
        tasks[task.id] = task
        return task
    }

    fun status(taskId: String): TaskReport = tasks[taskId]?.let { report(it) } ?: unknown(taskId)

    // ---- control -------------------------------------------------------------------------

    /** Checks a command and changes the task's state. Does not run the screen steps itself. */
    fun handle(command: ControlCommand, taskId: String): ControlOutcome {
        val t = tasks[taskId] ?: return ControlOutcome(unknown(taskId), null, false)
        if (command == ControlCommand.STATUS) return ControlOutcome(report(t), t, false)
        if (t.status.isFinished) {
            return refuse(t, "task_finished", "This task is already finished (${t.status.name.lowercase()}).")
        }
        return when (command) {
            ControlCommand.CONFIRM -> confirm(t)
            ControlCommand.REJECT -> reject(t)
            ControlCommand.RESUME -> resume(t)
            ControlCommand.CANCEL -> cancelCommand(t)
            ControlCommand.STATUS -> ControlOutcome(report(t), t, false)
        }
    }

    /** [handle], then carries on running when the command allows it. */
    suspend fun control(command: ControlCommand, taskId: String): TaskReport {
        val outcome = handle(command, taskId)
        val task = outcome.task
        return if (outcome.continueRun && task != null) run(task) else outcome.report
    }

    private fun confirm(t: SocialTask): ControlOutcome {
        if (t.request.mode == TaskMode.DRAFT) {
            return refuse(t, "confirm_not_pending", "This is a draft. Nothing is posted, so there is nothing to confirm.")
        }
        if (t.machine.state != TaskState.WAITING_FOR_CONFIRMATION || !t.confirmOffered) {
            return refuse(
                t, "confirm_not_pending",
                "Nothing is waiting for confirmation. Ask the user first, and only confirm after they clearly say yes.",
            )
        }
        if (t.confirmGranted) return refuse(t, "already_confirmed", "This task was already confirmed.")
        t.confirmGranted = true
        t.status = TaskStatus.RUNNING
        t.code = "confirmed"
        t.message = "Confirmed. Posting now."
        return ControlOutcome(report(t), t, true)
    }

    private fun reject(t: SocialTask): ControlOutcome {
        if (t.machine.state != TaskState.WAITING_FOR_CONFIRMATION) {
            return refuse(t, "nothing_to_reject", "Nothing is waiting for a yes or no.")
        }
        cancel(t, code = "rejected", message = "Understood. Nothing was posted.")
        return ControlOutcome(report(t), t, false)
    }

    private fun resume(t: SocialTask): ControlOutcome {
        if (t.machine.state != TaskState.WAITING_FOR_USER) {
            return refuse(t, "not_waiting_for_user", "The task is not waiting for the user.")
        }
        t.machine.moveTo(TaskState.OBSERVING, "user resumed")
        t.blocker = null
        t.status = TaskStatus.RUNNING
        t.code = "running"
        t.message = "Carrying on."
        return ControlOutcome(report(t), t, true)
    }

    private fun cancelCommand(t: SocialTask): ControlOutcome {
        if (t.running.get()) {
            t.cancelRequested = true
            t.code = "cancel_requested"
            t.message = "Cancelling after the current step."
            return ControlOutcome(report(t), t, false)
        }
        cancel(t)
        return ControlOutcome(report(t), t, false)
    }

    private fun refuse(t: SocialTask, code: String, message: String) =
        ControlOutcome(report(t, accepted = false, code = code, message = message), t, false)

    // ---- running -------------------------------------------------------------------------

    /** Runs until the task finishes or needs the user. Safe to call twice: the second call just reports. */
    suspend fun run(task: SocialTask): TaskReport {
        if (!task.running.compareAndSet(false, true)) return report(task)
        try {
            drive(task)
        } finally {
            task.running.set(false)
        }
        return report(task)
    }

    private suspend fun drive(t: SocialTask) {
        if (t.machine.isTerminal) return
        when (t.machine.state) {
            TaskState.WAITING_FOR_CONFIRMATION -> if (!t.confirmGranted) return
            TaskState.WAITING_FOR_USER -> return
            else -> Unit
        }
        t.status = TaskStatus.RUNNING
        while (true) {
            if (t.machine.isTerminal) return
            if (t.cancelRequested) {
                cancel(t)
                return
            }
            if (t.stepIndex >= t.plan.size) {
                finish(t)
                return
            }
            if (!runStep(t, t.plan[t.stepIndex])) return
        }
    }

    /** True: carry on with the next step. False: the task paused or finished. */
    private suspend fun runStep(t: SocialTask, step: PlanStep): Boolean = when (step) {
        is PlanStep.OpenApp -> openApp(t, step)
        is PlanStep.Tap -> tap(t, step)
        is PlanStep.TypeText -> typeText(t, step)
        is PlanStep.Back -> back(t)
        is PlanStep.Publish -> publish(t, step)
    }

    private suspend fun openApp(t: SocialTask, step: PlanStep.OpenApp): Boolean {
        if (!t.appLaunched) {
            go(t, TaskState.OPENING_APP)
            if (!opener.open(step.packageName, step.mediaUri)) {
                return fail(t, FailureReason.WRONG_APP, "Could not open ${t.request.platform.displayName}. Is it installed?", "app_not_available")
            }
            t.appLaunched = true
            pause(1500)
        }
        go(t, TaskState.OBSERVING)
        var screen: ScreenObservation? = null
        for (attempt in 1..6) {
            screen = executor.observe()
            if (screen != null && screen.packageName == step.packageName) break
            if (attempt < 6) pause(500)
        }
        if (screen == null) return fail(t, FailureReason.NO_SCREEN, "Could not read the screen.")
        val blocker = detector.detect(screen)
        if (blocker != null) return block(t, blocker)
        if (screen.packageName != step.packageName) {
            return fail(t, FailureReason.WRONG_APP, "${t.request.platform.displayName} did not come to the front.")
        }
        go(t, TaskState.NEXT_STEP)
        t.stepIndex++
        return true
    }

    private suspend fun tap(t: SocialTask, step: PlanStep.Tap): Boolean {
        val pkg = t.request.platform.packageName
        val skip = step.skipIfPresent
        if (skip != null) {
            go(t, TaskState.OBSERVING)
            val screen = executor.observe()
            if (screen != null) {
                val blocker = detector.detect(screen)
                if (blocker != null) return block(t, blocker)
                if (resolver.resolve(screen, skip) !is Resolution.NotFound) {
                    go(t, TaskState.NEXT_STEP)
                    t.stepIndex++
                    return true
                }
            }
        }
        go(t, TaskState.RESOLVING_TARGET)
        when (val located = executor.locate(step.spec, pkg)) {
            is Located.Stopped -> return stopped(t, located.result)
            is Located.Found -> Unit
        }
        go(t, TaskState.VALIDATING_TARGET)
        go(t, TaskState.EXECUTING_ACTION)
        val result = executor.tap(step.spec, step.expect, pkg)
        go(t, TaskState.VERIFYING)
        return afterAction(t, result)
    }

    private suspend fun typeText(t: SocialTask, step: PlanStep.TypeText): Boolean {
        val pkg = t.request.platform.packageName
        go(t, TaskState.RESOLVING_TARGET)
        when (val located = executor.locate(step.field.copy(mustBeEditable = true), pkg)) {
            is Located.Stopped -> return stopped(t, located.result)
            is Located.Found -> Unit
        }
        go(t, TaskState.VALIDATING_TARGET)
        go(t, TaskState.EXECUTING_ACTION)
        val result = typer.typeInto(step.field, step.text, pkg)
        go(t, TaskState.VERIFYING)
        return afterAction(t, result)
    }

    private suspend fun back(t: SocialTask): Boolean {
        go(t, TaskState.RESOLVING_TARGET)
        go(t, TaskState.VALIDATING_TARGET)
        go(t, TaskState.EXECUTING_ACTION)
        val done = driver.back()
        pause(600)
        go(t, TaskState.VERIFYING)
        if (done != DriverResult.OK) return fail(t, FailureReason.ACTION_FAILED, "Could not press Back.")
        go(t, TaskState.NEXT_STEP)
        t.stepIndex++
        return true
    }

    private suspend fun publish(t: SocialTask, step: PlanStep.Publish): Boolean {
        // A draft can never reach the irreversible tap, even if a bug put the step in its plan.
        if (t.request.mode == TaskMode.DRAFT) {
            return fail(t, FailureReason.UNSUPPORTED, "A draft never publishes.", "draft_cannot_publish")
        }
        val pkg = t.request.platform.packageName

        if (t.machine.state != TaskState.WAITING_FOR_CONFIRMATION) {
            // First visit: find the button, then ask. Nothing is tapped yet.
            go(t, TaskState.RESOLVING_TARGET)
            when (val located = executor.locate(step.spec, pkg)) {
                is Located.Stopped -> return stopped(t, located.result)
                is Located.Found -> Unit
            }
            go(t, TaskState.VALIDATING_TARGET)
            go(t, TaskState.WAITING_FOR_CONFIRMATION)
            t.confirmOffered = true
            t.confirmGranted = false
            t.status = TaskStatus.WAITING_FOR_CONFIRMATION
            t.code = "waiting_for_confirmation"
            t.message = confirmationText(t.request)
            return false
        }

        // Second visit: only here because CONFIRM was accepted. Without it, tap nothing.
        if (!t.confirmGranted) return false
        t.confirmGranted = false // one yes buys one tap
        go(t, TaskState.EXECUTING_ACTION)
        val result = executor.tap(step.spec, step.expect, pkg, retryOnUnverified = false)
        go(t, TaskState.VERIFYING)
        if (result is ActionResult.Failed && result.reason == FailureReason.VERIFICATION_FAILED) {
            // The tap was made but nothing proved it worked. Never tap again: that could post twice.
            t.machine.moveTo(TaskState.COMPLETED, "submitted, not verified")
            t.status = TaskStatus.SUBMITTED_UNVERIFIED
            t.code = "submitted_unverified"
            t.message = "I tapped ${step.spec.label} but could not confirm that it was published. " +
                "Tell the user to open ${t.request.platform.displayName} and check before trying again."
            return false
        }
        return afterAction(t, result)
    }

    // ---- shared endings ------------------------------------------------------------------

    /** The state is VERIFYING. */
    private fun afterAction(t: SocialTask, result: ActionResult): Boolean = when (result) {
        is ActionResult.Success -> {
            go(t, TaskState.NEXT_STEP)
            t.stepIndex++
            true
        }
        is ActionResult.Blocked -> block(t, result.blocker)
        is ActionResult.Failed -> fail(t, result.reason, failureText(result))
    }

    /** The state is RESOLVING_TARGET. */
    private fun stopped(t: SocialTask, result: ActionResult): Boolean = when (result) {
        is ActionResult.Blocked -> block(t, result.blocker)
        is ActionResult.Failed -> fail(t, result.reason, failureText(result))
        is ActionResult.Success -> true
    }

    private fun failureText(result: ActionResult.Failed): String =
        result.detail.ifBlank { result.reason.code.replace('_', ' ') }

    private fun block(t: SocialTask, blocker: Blocker): Boolean {
        go(t, TaskState.WAITING_FOR_USER)
        t.status = TaskStatus.WAITING_FOR_USER
        t.blocker = blocker
        t.code = "waiting_for_user"
        val app = t.request.platform.displayName
        t.message = when (blocker) {
            Blocker.LOGIN -> "$app is asking for a login. The user must log in themselves, then say resume."
            Blocker.CAPTCHA -> "$app is showing a captcha. The user must solve it, then say resume."
            Blocker.TWO_FACTOR -> "$app wants a security code. The user must enter it, then say resume."
            Blocker.SECURITY_CHECK -> "$app is running a security check. The user must finish it, then say resume."
            Blocker.PERMISSION_PROMPT -> "A permission question is on screen. The user must answer it, then say resume."
        }
        return false
    }

    private fun fail(t: SocialTask, reason: FailureReason, message: String, code: String = reason.code): Boolean {
        t.machine.moveTo(TaskState.FAILED, code)
        t.status = TaskStatus.FAILED
        t.failure = reason
        t.code = code
        t.message = "Stopped: $message"
        return false
    }

    private fun cancel(t: SocialTask, code: String = "cancelled", message: String = "Cancelled.") {
        if (!t.machine.isTerminal) t.machine.moveTo(TaskState.CANCELLED, code)
        t.status = TaskStatus.CANCELLED
        t.code = code
        t.message = message
    }

    private fun finish(t: SocialTask) {
        t.machine.moveTo(TaskState.COMPLETED, "all steps done")
        if (t.request.mode == TaskMode.DRAFT) {
            t.status = TaskStatus.DRAFT_SAVED
            t.code = "draft_saved"
            t.message = "Saved as a draft on ${t.request.platform.displayName}. Nothing was posted."
        } else {
            t.status = TaskStatus.COMPLETED
            t.code = "completed"
            t.message = "Published on ${t.request.platform.displayName}."
        }
    }

    private fun go(t: SocialTask, to: TaskState) {
        if (t.machine.state != to) t.machine.moveTo(to)
    }

    private fun confirmationText(r: SocialTaskRequest): String {
        val what = when (r.action) {
            SocialAction.CREATE_POST -> "a post"
            SocialAction.CREATE_REEL -> "a reel"
            SocialAction.CREATE_STORY -> "a story"
            SocialAction.TEXT_POST -> "a text post"
        }
        val account = r.targetAccount?.let { " as $it" }.orEmpty()
        val caption = if (r.caption.isNotEmpty()) " Caption: \"${r.caption.take(120)}\"." else ""
        return "Ready to publish $what on ${r.platform.displayName}$account.$caption " +
            "Ask the user to confirm. Only call confirm after they explicitly said yes."
    }

    private fun report(
        t: SocialTask,
        accepted: Boolean = true,
        code: String = t.code,
        message: String = t.message,
    ) = TaskReport(
        taskId = t.id,
        status = t.status,
        code = code,
        message = message,
        state = t.machine.state,
        step = t.stepIndex,
        totalSteps = t.plan.size,
        accepted = accepted,
        needsConfirmation = t.status == TaskStatus.WAITING_FOR_CONFIRMATION,
        blocker = t.blocker,
        failure = t.failure,
    )

    private fun unknown(taskId: String) = TaskReport(
        taskId = taskId,
        status = TaskStatus.FAILED,
        code = "task_not_found",
        message = "There is no task with id $taskId.",
        state = TaskState.IDLE,
        step = 0,
        totalSteps = 0,
        accepted = false,
    )
}
