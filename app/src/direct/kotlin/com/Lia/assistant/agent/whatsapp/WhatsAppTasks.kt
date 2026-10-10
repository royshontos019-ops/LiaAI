package com.Lia.assistant.agent.whatsapp

import com.Lia.assistant.agent.social.ControlCommand
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class WhatsAppTask internal constructor(val id: String, val request: WhatsAppRequest) {
    @Volatile var status = WhatsAppStatus.RUNNING
    @Volatile var outcome: WhatsAppOutcome? = null
    internal var job: Job? = null
}

data class WhatsAppReport(
    val taskId: String,
    val status: WhatsAppStatus,
    val code: String,
    val message: String,
    val items: List<String>,
    val accepted: Boolean = true,
)

/** Runs WhatsApp actions in the background, one at a time, so the voice tool can answer at once. */
class WhatsAppTaskManager(
    private val agent: WhatsAppAgent,
    private val scope: CoroutineScope,
    private val newId: () -> String = { UUID.randomUUID().toString().take(8) },
) {
    private val tasks = ConcurrentHashMap<String, WhatsAppTask>()

    fun active(): WhatsAppTask? = tasks.values.firstOrNull { !it.status.isFinished }

    @Synchronized
    fun start(request: WhatsAppRequest): WhatsAppTask? {
        if (active() != null) return null
        val task = WhatsAppTask(newId(), request)
        tasks[task.id] = task
        begin(task)
        return task
    }

    private fun begin(task: WhatsAppTask) {
        task.status = WhatsAppStatus.RUNNING
        task.job = scope.launch {
            val result = agent.run(task.request)
            if (task.status != WhatsAppStatus.CANCELLED) {
                task.outcome = result
                task.status = result.status
            }
        }
    }

    fun status(taskId: String): WhatsAppReport = tasks[taskId]?.let { report(it) } ?: unknown(taskId)

    fun control(command: ControlCommand, taskId: String): WhatsAppReport {
        val task = tasks[taskId] ?: return unknown(taskId)
        return when (command) {
            ControlCommand.STATUS -> report(task)
            ControlCommand.CONFIRM, ControlCommand.REJECT ->
                refuse(task, "not_supported", "WhatsApp actions do not ask for confirmation.")
            ControlCommand.CANCEL -> {
                if (task.status.isFinished) return refuse(task, "task_finished", "This task is already finished.")
                task.status = WhatsAppStatus.CANCELLED
                task.job?.cancel()
                task.outcome = WhatsAppOutcome(WhatsAppStatus.CANCELLED, "cancelled", "Cancelled.")
                report(task)
            }
            ControlCommand.RESUME -> {
                if (task.status != WhatsAppStatus.WAITING_FOR_USER) {
                    return refuse(task, "not_waiting_for_user", "The task is not waiting for the user.")
                }
                begin(task)
                report(task)
            }
        }
    }

    private fun report(t: WhatsAppTask): WhatsAppReport {
        val o = t.outcome
        return WhatsAppReport(
            taskId = t.id,
            status = t.status,
            code = o?.code ?: "running",
            message = o?.message ?: "Working on it.",
            items = o?.items.orEmpty(),
        )
    }

    private fun refuse(t: WhatsAppTask, code: String, message: String) =
        report(t).copy(code = code, message = message, accepted = false)

    private fun unknown(taskId: String) = WhatsAppReport(
        taskId = taskId,
        status = WhatsAppStatus.FAILED,
        code = "task_not_found",
        message = "There is no task with id $taskId.",
        items = emptyList(),
        accepted = false,
    )
}
