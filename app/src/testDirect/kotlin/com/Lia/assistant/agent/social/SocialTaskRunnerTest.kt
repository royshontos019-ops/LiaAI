package com.Lia.assistant.agent.social

import com.Lia.assistant.agent.FakeScreen
import com.Lia.assistant.agent.FakeScreenDriver
import com.Lia.assistant.agent.node
import com.Lia.assistant.agent.core.ActionTimings
import com.Lia.assistant.agent.core.Blocker
import com.Lia.assistant.agent.core.DriverResult
import com.Lia.assistant.agent.core.FailureReason
import com.Lia.assistant.agent.core.ScreenObserver
import com.Lia.assistant.agent.core.TargetResolver
import com.Lia.assistant.agent.core.TaskState
import com.Lia.assistant.agent.core.TextInputExecutor
import com.Lia.assistant.agent.core.UiActionExecutor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val IG = "com.instagram.android"

/** An Instagram that behaves like the real one closely enough to exercise every rule. */
private fun instagramWorld(): Map<String, FakeScreen> = mapOf(
    "login" to FakeScreen(
        IG,
        listOf(
            node(0, "Log in"),
            node(1, id = "$IG:id/password", cls = "android.widget.EditText", editable = true, password = true),
            node(2, "Forgot password?", clickable = true),
        ),
    ),
    "editor" to FakeScreen(
        IG,
        listOf(node(0, "Next", id = "$IG:id/next_button_textview", clickable = true)),
        onClick = mapOf("Next" to "caption"),
    ),
    "caption" to FakeScreen(
        IG,
        listOf(
            node(0, "Share", id = "$IG:id/share_footer_button", clickable = true),
            node(1, id = "$IG:id/caption_text_view", cls = "android.widget.EditText", editable = true, focused = true, clickable = true),
        ),
        onClick = mapOf("Share" to "sharing"),
        onBack = "draftDialog",
    ),
    "sharing" to FakeScreen(IG, listOf(node(0, "Sharing\u2026"))),
    "draftDialog" to FakeScreen(
        IG,
        listOf(node(0, "Save this post as a draft?"), node(1, "Save draft", clickable = true), node(2, "Discard", clickable = true)),
        onClick = mapOf("Save draft" to "feed", "Discard" to "feed"),
    ),
    "feed" to FakeScreen(IG, listOf(node(0, "Home feed"))),
    "captcha" to FakeScreen(IG, listOf(node(0, "Select all images with traffic lights"), node(1, "I'm not a robot"))),
)

private class FakeOpener(private val driver: FakeScreenDriver, private val screen: String, private val ok: Boolean = true) : AppOpener {
    val opened = mutableListOf<Pair<String, String?>>()

    override suspend fun open(packageName: String, mediaUri: String?): Boolean {
        opened += packageName to mediaUri
        if (!ok) return false
        driver.current = screen
        return true
    }
}

private class Harness(
    screens: Map<String, FakeScreen> = instagramWorld(),
    startScreen: String = "editor",
    planner: SocialPlanner = SocialPlanner(),
    openerOk: Boolean = true,
) {
    val driver = FakeScreenDriver(screens, startScreen)
    val opener = FakeOpener(driver, startScreen, openerOk)

    private val detector = BlockerDetector()
    private val timings = ActionTimings(
        resolveAttempts = 2, resolveIntervalMs = 0, settleMs = 0,
        verifyAttempts = 2, verifyIntervalMs = 0, maxStaleRetries = 2,
    )
    private val noPause: suspend (Long) -> Unit = {}
    private var ids = 0

    val executor = UiActionExecutor(
        driver = driver, observer = ScreenObserver(), resolver = TargetResolver(),
        blockerCheck = detector::detect, timings = timings, pause = noPause,
    )
    private val typer = TextInputExecutor(driver, executor, timings, noPause)
    val runner = SocialTaskRunner(driver, executor, typer, opener, detector, planner, noPause) { "t${++ids}" }

    fun newTask(request: SocialTaskRequest): SocialTask = runner.create(request) ?: error("another task is still running")
}

private fun request(
    mode: TaskMode = TaskMode.PUBLISH,
    action: SocialAction = SocialAction.CREATE_POST,
    caption: String = "Hello",
) = SocialTaskRequest(Platform.INSTAGRAM, action, "content://lia/photo", caption, mode, "lia.test")

class SocialTaskRunnerTest {

    // ---- PUBLISH asks first --------------------------------------------------------------

    @Test fun publishPausesBeforeTheIrreversibleTap() = runTest {
        val h = Harness()
        val task = h.newTask(request())
        val report = h.runner.run(task)

        assertEquals(TaskStatus.WAITING_FOR_CONFIRMATION, report.status)
        assertEquals(TaskState.WAITING_FOR_CONFIRMATION, report.state)
        assertTrue(report.needsConfirmation)
        assertTrue("the ask must say when to confirm", report.message.contains("explicitly said yes"))
        assertFalse("Share must not be tapped before a yes", h.driver.clicked("Share"))
        assertTrue("the caption was typed", h.driver.log.contains("text:Hello"))
        assertEquals(listOf(IG to "content://lia/photo"), h.opener.opened)
    }

    @Test fun confirmThenPostsExactlyOnce() = runTest {
        val h = Harness()
        val task = h.newTask(request())
        h.runner.run(task)

        val report = h.runner.control(ControlCommand.CONFIRM, task.id)
        assertEquals(TaskStatus.COMPLETED, report.status)
        assertEquals(TaskState.COMPLETED, report.state)
        assertEquals(1, h.driver.clickCount("Share"))

        val again = h.runner.control(ControlCommand.CONFIRM, task.id)
        assertFalse(again.accepted)
        assertEquals("task_finished", again.code)
        assertEquals("a second confirm must not post again", 1, h.driver.clickCount("Share"))
    }

    @Test fun rejectCancelsAndNeverPosts() = runTest {
        val h = Harness()
        val task = h.newTask(request())
        h.runner.run(task)

        val report = h.runner.control(ControlCommand.REJECT, task.id)
        assertEquals(TaskStatus.CANCELLED, report.status)
        assertEquals("rejected", report.code)
        assertFalse(h.driver.clicked("Share"))
        assertFalse(h.runner.control(ControlCommand.CONFIRM, task.id).accepted)
        assertFalse(h.driver.clicked("Share"))
    }

    // ---- confirm without a prior yes -----------------------------------------------------

    @Test fun confirmBeforeTheTaskHasAskedIsRefused() = runTest {
        val h = Harness()
        val task = h.newTask(request())

        val report = h.runner.control(ControlCommand.CONFIRM, task.id)
        assertFalse(report.accepted)
        assertEquals("confirm_not_pending", report.code)
        assertEquals(TaskStatus.QUEUED, report.status)
        assertTrue("nothing at all was done", h.driver.log.isEmpty())
        assertTrue(h.opener.opened.isEmpty())
    }

    @Test fun confirmWhileStillWorkingOrBlockedIsRefused() = runTest {
        val h = Harness(startScreen = "login")
        val task = h.newTask(request())
        h.runner.run(task) // stops at the login screen

        val report = h.runner.control(ControlCommand.CONFIRM, task.id)
        assertFalse(report.accepted)
        assertEquals("confirm_not_pending", report.code)
        assertEquals(TaskStatus.WAITING_FOR_USER, report.status)
        assertFalse(h.driver.clicked("Share"))
    }

    @Test fun confirmCannotBeUsedTwiceOnTheSamePause() {
        val h = Harness()
        val task = h.newTask(request())
        kotlinx.coroutines.runBlocking { h.runner.run(task) }

        val first = h.runner.handle(ControlCommand.CONFIRM, task.id)
        val second = h.runner.handle(ControlCommand.CONFIRM, task.id)
        assertTrue(first.report.accepted && first.continueRun)
        assertFalse(second.report.accepted)
        assertEquals("already_confirmed", second.report.code)
    }

    @Test fun unknownTaskIdsAreRefused() = runTest {
        val h = Harness()
        val report = h.runner.control(ControlCommand.CONFIRM, "nope")
        assertFalse(report.accepted)
        assertEquals("task_not_found", report.code)
    }

    // ---- DRAFT never publishes -----------------------------------------------------------

    @Test fun aDraftIsSavedAndNeverPublished() = runTest {
        val h = Harness()
        val task = h.newTask(request(mode = TaskMode.DRAFT))
        assertTrue("a draft plan has no publish step", task.plan.none { it is PlanStep.Publish })

        val report = h.runner.run(task)
        assertEquals(TaskStatus.DRAFT_SAVED, report.status)
        assertFalse(report.needsConfirmation)
        assertFalse(h.driver.clicked("Share"))
        assertTrue(h.driver.clicked("Save draft"))
        assertTrue(h.driver.log.contains("back"))
    }

    @Test fun confirmOnADraftIsRefused() = runTest {
        val h = Harness()
        val task = h.newTask(request(mode = TaskMode.DRAFT))
        h.runner.run(task)
        val report = h.runner.control(ControlCommand.CONFIRM, task.id)
        assertFalse(report.accepted)
        assertFalse(h.driver.clicked("Share"))
    }

    @Test fun aPublishStepInsideADraftIsStillRefused() = runTest {
        // Defence in depth: even a broken planner cannot make a draft post.
        val sneaky = object : SocialPlanner() {
            override fun plan(request: SocialTaskRequest): List<PlanStep> {
                val s = SocialSelectors.forPlatform(request.platform)
                return listOf(
                    PlanStep.OpenApp("open", request.platform.packageName, null),
                    PlanStep.Publish("Share the post", s.share, s.published),
                )
            }
        }
        val h = Harness(startScreen = "caption", planner = sneaky)
        val task = h.newTask(request(mode = TaskMode.DRAFT))

        val report = h.runner.run(task)
        assertEquals(TaskStatus.FAILED, report.status)
        assertEquals("draft_cannot_publish", report.code)
        assertFalse(h.driver.clicked("Share"))
    }

    // ---- blockers pause for the user -----------------------------------------------------

    @Test fun aLoginScreenPausesTheTaskForTheUser() = runTest {
        val h = Harness(startScreen = "login")
        val task = h.newTask(request())

        val report = h.runner.run(task)
        assertEquals(TaskStatus.WAITING_FOR_USER, report.status)
        assertEquals(TaskState.WAITING_FOR_USER, report.state)
        assertEquals(Blocker.LOGIN, report.blocker)
        assertTrue(h.driver.log.none { it.startsWith("click:") })

        // The user logs in, then says resume: the task goes on to the question.
        h.driver.current = "editor"
        val resumed = h.runner.control(ControlCommand.RESUME, task.id)
        assertEquals(TaskStatus.WAITING_FOR_CONFIRMATION, resumed.status)
        assertNull(resumed.blocker)
        assertEquals("the app was not opened a second time", 1, h.opener.opened.size)
        assertFalse(h.driver.clicked("Share"))
    }

    @Test fun aCaptchaInTheMiddleOfAStepPausesTheTask() = runTest {
        val world = instagramWorld().toMutableMap()
        world["editor"] = FakeScreen(
            IG, listOf(node(0, "Next", id = "$IG:id/next_button_textview", clickable = true)),
            onClick = mapOf("Next" to "captcha"),
        )
        val h = Harness(screens = world)
        val task = h.newTask(request())

        val report = h.runner.run(task)
        assertEquals(TaskStatus.WAITING_FOR_USER, report.status)
        assertEquals(Blocker.CAPTCHA, report.blocker)

        h.driver.current = "caption" // the user solved it and the app moved on
        val resumed = h.runner.control(ControlCommand.RESUME, task.id)
        assertEquals(TaskStatus.WAITING_FOR_CONFIRMATION, resumed.status)
        assertFalse(h.driver.clicked("Share"))
    }

    @Test fun resumeOnlyWorksWhileWaitingForTheUser() = runTest {
        val h = Harness()
        val task = h.newTask(request())
        h.runner.run(task) // waiting for confirmation, not for the user
        val report = h.runner.control(ControlCommand.RESUME, task.id)
        assertFalse(report.accepted)
        assertEquals("not_waiting_for_user", report.code)
    }

    // ---- stale observations --------------------------------------------------------------

    @Test fun theDriverRefusesAnObservationThatIsNoLongerTheLatest() = runTest {
        val driver = FakeScreenDriver(instagramWorld(), "caption")
        val observer = ScreenObserver()
        val old = observer.observe(driver.capture()!!)
        val newer = observer.observe(driver.capture()!!)
        val share = old.elements.first { it.label == "Share" }

        assertEquals(DriverResult.STALE, driver.clickElement(old, share))
        assertEquals(DriverResult.OK, driver.clickElement(newer, newer.elements.first { it.label == "Share" }))
        // After an action the observation that was just used is stale as well.
        assertEquals(DriverResult.STALE, driver.clickElement(newer, share))
    }

    @Test fun aStaleClickIsRetriedOnAFreshScreen() = runTest {
        val h = Harness()
        val task = h.newTask(request())
        h.runner.run(task)

        h.driver.staleClicks = 1
        val report = h.runner.control(ControlCommand.CONFIRM, task.id)
        assertEquals(TaskStatus.COMPLETED, report.status)
        assertEquals("one real tap, not two", 1, h.driver.clickCount("Share"))
    }

    @Test fun aScreenThatNeverSettlesFailsInsteadOfGuessing() = runTest {
        val h = Harness()
        val task = h.newTask(request())
        h.runner.run(task)

        h.driver.staleClicks = 10
        val report = h.runner.control(ControlCommand.CONFIRM, task.id)
        assertEquals(TaskStatus.FAILED, report.status)
        assertEquals(FailureReason.STALE_OBSERVATION, report.failure)
        assertFalse(h.driver.clicked("Share"))
    }

    // ---- unverified submission -----------------------------------------------------------

    @Test fun aTapThatCannotBeVerifiedIsReportedAsSubmittedUnverified() = runTest {
        val h = Harness()
        val task = h.newTask(request())
        h.runner.run(task)

        h.driver.noEffectLabels += "Share" // the tap lands but the app never shows it is posting
        val report = h.runner.control(ControlCommand.CONFIRM, task.id)

        assertEquals(TaskStatus.SUBMITTED_UNVERIFIED, report.status)
        assertEquals("submitted_unverified", report.code)
        assertTrue("it must tell the user to check", report.message.contains("check"))
        assertTrue(report.message.contains("could not confirm"))
        assertEquals("never tap Share a second time", 1, h.driver.clickCount("Share"))
        assertTrue(task.machine.isTerminal)
    }

    // ---- failures and bookkeeping --------------------------------------------------------

    @Test fun anAppThatCannotBeOpenedFailsCleanly() = runTest {
        val h = Harness(openerOk = false)
        val task = h.newTask(request())
        val report = h.runner.run(task)
        assertEquals(TaskStatus.FAILED, report.status)
        assertEquals("app_not_available", report.code)
    }

    @Test fun onlyOneTaskRunsAtATime() = runTest {
        val h = Harness()
        val first = h.newTask(request())
        assertNull(h.runner.create(request()))
        h.runner.run(first)
        assertNull("still waiting for confirmation", h.runner.create(request()))
        h.runner.control(ControlCommand.CANCEL, first.id)
        assertNotNull(h.runner.create(request()))
    }

    @Test fun cancelStopsAPausedTask() = runTest {
        val h = Harness()
        val task = h.newTask(request())
        h.runner.run(task)
        val report = h.runner.control(ControlCommand.CANCEL, task.id)
        assertEquals(TaskStatus.CANCELLED, report.status)
        assertEquals(TaskState.CANCELLED, report.state)
        assertFalse(h.driver.clicked("Share"))
    }

    @Test fun statusAlwaysAnswersAndChangesNothing() = runTest {
        val h = Harness()
        val task = h.newTask(request())
        val before = h.runner.control(ControlCommand.STATUS, task.id)
        assertTrue(before.accepted)
        assertEquals(TaskStatus.QUEUED, before.status)
        h.runner.run(task)
        assertEquals(TaskStatus.WAITING_FOR_CONFIRMATION, h.runner.status(task.id).status)
    }

    @Test fun everyMoveTheTaskMadeWasLegal() = runTest {
        val h = Harness()
        val task = h.newTask(request())
        h.runner.run(task)
        h.runner.control(ControlCommand.CONFIRM, task.id)
        // moveTo() throws on an illegal move, so reaching here means none happened. Check the log too.
        for (t in task.machine.history) {
            assertTrue("${t.from} -> ${t.to}", com.Lia.assistant.agent.core.TaskStateMachine.isLegal(t.from, t.to))
        }
        assertEquals(TaskState.IDLE, task.machine.history.first().from)
        assertEquals(TaskState.COMPLETED, task.machine.history.last().to)
    }

    @Test fun theSocialPlannerAsksOnlyForThePublishStep() {
        val planner = SocialPlanner()
        for (platform in Platform.entries) {
            for (action in SocialAction.entries) {
                val caption = if (action == SocialAction.CREATE_STORY) "" else "hi"
                val publish = planner.plan(SocialTaskRequest(platform, action, if (action.needsMedia) "content://x" else null, caption, TaskMode.PUBLISH, null))
                assertEquals("$platform $action", 1, publish.count { it is PlanStep.Publish })
                assertTrue("publish is last", publish.last() is PlanStep.Publish)
                if (action.allowsDraft) {
                    val draft = planner.plan(SocialTaskRequest(platform, action, if (action.needsMedia) "content://x" else null, caption, TaskMode.DRAFT, null))
                    assertEquals("$platform $action draft", 0, draft.count { it is PlanStep.Publish })
                }
            }
        }
    }
}
