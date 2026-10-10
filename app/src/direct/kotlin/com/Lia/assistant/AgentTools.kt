package com.Lia.assistant

import android.content.Context
import com.Lia.assistant.action.ToolJson
import com.Lia.assistant.action.functionDecl
import com.Lia.assistant.agent.core.AccessibilityScreenDriver
import com.Lia.assistant.agent.core.TextInputExecutor
import com.Lia.assistant.agent.core.UiActionExecutor
import com.Lia.assistant.agent.social.AndroidAppOpener
import com.Lia.assistant.agent.social.BlockerDetector
import com.Lia.assistant.agent.social.ControlCommand
import com.Lia.assistant.agent.social.MediaResolver
import com.Lia.assistant.agent.social.ParseResult
import com.Lia.assistant.agent.social.SharedMediaStore
import com.Lia.assistant.agent.social.SocialTaskRequest
import com.Lia.assistant.agent.social.SocialTaskRunner
import com.Lia.assistant.agent.social.TaskReport
import com.Lia.assistant.agent.whatsapp.AndroidWhatsAppLauncher
import com.Lia.assistant.agent.whatsapp.WhatsAppAgent
import com.Lia.assistant.agent.whatsapp.WhatsAppNotificationStore
import com.Lia.assistant.agent.whatsapp.WhatsAppParse
import com.Lia.assistant.agent.whatsapp.WhatsAppReport
import com.Lia.assistant.agent.whatsapp.WhatsAppRequest
import com.Lia.assistant.agent.whatsapp.WhatsAppTaskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Direct flavor: the four agent tools. Long work runs in the background and the tool answers at once. */
object AgentTools {
    const val SOCIAL_TASK = "execute_social_media_task"
    const val SOCIAL_CONTROL = "social_media_task_control"
    const val WHATSAPP_TASK = "execute_whatsapp_task"
    const val WHATSAPP_CONTROL = "whatsapp_task_control"

    val names: Set<String> = setOf(SOCIAL_TASK, SOCIAL_CONTROL, WHATSAPP_TASK, WHATSAPP_CONTROL)

    fun declarations(): List<JSONObject> = listOf(
        functionDecl(
            SOCIAL_TASK,
            "Post to Instagram or Facebook by driving the app on screen. Media must be a photo or video the user shared to Lia. " +
                "mode publish asks the user to confirm before posting; mode draft only saves a draft and never posts. " +
                "The task runs in the background: it returns a task_id, and you then watch it with social_media_task_control (status).",
            mapOf(
                "platform" to "instagram or facebook",
                "action" to "create_post, create_reel, create_story or text_post",
                "media_uri" to "content:// address of the media the user shared to Lia (not needed for text_post)",
                "caption" to "Caption or post text (optional, not used for stories)",
                "mode" to "publish or draft (default draft)",
                "target_account" to "Account name to post as (optional; the app's current account is used)",
            ),
            listOf("platform", "action"),
        ),
        functionDecl(
            SOCIAL_CONTROL,
            "Control a social media task. status: how it is going. confirm: post now. reject: do not post. " +
                "resume: carry on after the user dealt with a login, captcha or code. cancel: stop. " +
                "Only call confirm after the user explicitly said yes to posting.",
            mapOf(
                "command" to "confirm, reject, resume, cancel or status",
                "task_id" to "The task_id returned by execute_social_media_task (optional while one task is active)",
            ),
            listOf("command"),
        ),
        functionDecl(
            WHATSAPP_TASK,
            "Do something in WhatsApp. read_unread reads unread messages from notifications without opening WhatsApp. " +
                "send_message needs a phone number with country code. read_chat, mute_chat, unmute_chat and mark_read take a " +
                "contact_name or phone_number. search_chat takes a query. If two contacts match a name, the task fails with " +
                "contact_ambiguous: ask the user which one. Other actions run in the background and return a task_id.",
            mapOf(
                "action" to "read_unread, send_message, read_chat, search_chat, mute_chat, unmute_chat or mark_read",
                "phone_number" to "Phone number with country code, for example +8801712345678",
                "contact_name" to "Name of the contact or chat",
                "message" to "Text to send (send_message)",
                "query" to "Text to search for (search_chat)",
                "limit" to "How many messages or chats to return (default 10)",
            ),
            listOf("action"),
        ),
        functionDecl(
            WHATSAPP_CONTROL,
            "Control a WhatsApp task. status: how it is going. resume: carry on after the user dealt with a prompt. cancel: stop.",
            mapOf(
                "command" to "resume, cancel or status",
                "task_id" to "The task_id returned by execute_whatsapp_task (optional while one task is active)",
            ),
            listOf("command"),
        ),
    )

    suspend fun execute(context: Context, name: String, args: JSONObject): JSONObject {
        if (!ActionExecutor.isAvailable) {
            return reply("accessibility_off").put("error", "Screen control is off. Ask the user to turn on Lia in Accessibility settings.")
        }
        val rt = AgentRuntime.get(context)
        return when (name) {
            SOCIAL_TASK -> startSocial(rt, args)
            SOCIAL_CONTROL -> controlSocial(rt, args)
            WHATSAPP_TASK -> startWhatsApp(rt, args)
            WHATSAPP_CONTROL -> controlWhatsApp(rt, args)
            else -> reply("unknown_tool").put("error", "Unknown tool: $name")
        }
    }

    // ---- social --------------------------------------------------------------------------

    private fun startSocial(rt: AgentRuntime, args: JSONObject): JSONObject {
        val map = listOf("platform", "action", "media_uri", "caption", "mode", "target_account")
            .associateWith { args.optString(it) }
        val request: SocialTaskRequest = when (val parsed = SocialTaskRequest.parse(map, MediaResolver(SharedMediaStore))) {
            is ParseResult.Invalid -> return reply("invalid_request").put("code", parsed.code).put("error", parsed.message)
            is ParseResult.Valid -> parsed.request
        }
        busyReply(rt)?.let { return it }
        val task = rt.social.create(request) ?: return reply("busy").put("error", "Another task is still running.")
        rt.scope.launch { rt.social.run(task) }
        return reply("started")
            .put("task_id", task.id)
            .put("message", "Started. Check progress with social_media_task_control status.")
    }

    private fun controlSocial(rt: AgentRuntime, args: JSONObject): JSONObject {
        val command = ControlCommand.fromCode(args.optString("command"))
            ?: return reply("invalid_command").put("error", "command must be confirm, reject, resume, cancel or status")
        val id = args.optString("task_id").ifBlank { rt.social.active()?.id.orEmpty() }
        if (id.isBlank()) return ToolJson.missing("task_id")
        val outcome = rt.social.handle(command, id)
        val task = outcome.task
        if (outcome.continueRun && task != null) rt.scope.launch { rt.social.run(task) }
        return outcome.report.toJson()
    }

    // ---- whatsapp ------------------------------------------------------------------------

    private suspend fun startWhatsApp(rt: AgentRuntime, args: JSONObject): JSONObject {
        val map = listOf("action", "phone_number", "contact_name", "message", "query", "limit")
            .associateWith { args.optString(it) }
        val request: WhatsAppRequest = when (val parsed = WhatsAppRequest.parse(map)) {
            is WhatsAppParse.Invalid -> return reply("invalid_request").put("code", parsed.code).put("error", parsed.message)
            is WhatsAppParse.Valid -> parsed.request
        }
        // Reading unread messages needs no screen, so it answers on the spot.
        if (request.action == com.Lia.assistant.agent.whatsapp.WhatsAppAction.READ_UNREAD) {
            val outcome = rt.whatsappAgent.run(request)
            return reply(outcome.code).put("message", outcome.message).put("items", JSONArray(outcome.items))
        }
        busyReply(rt)?.let { return it }
        val task = rt.whatsapp.start(request) ?: return reply("busy").put("error", "Another task is still running.")
        return reply("started")
            .put("task_id", task.id)
            .put("message", "Started. Check progress with whatsapp_task_control status.")
    }

    private fun controlWhatsApp(rt: AgentRuntime, args: JSONObject): JSONObject {
        val command = ControlCommand.fromCode(args.optString("command"))
            ?: return reply("invalid_command").put("error", "command must be resume, cancel or status")
        val id = args.optString("task_id").ifBlank { rt.whatsapp.active()?.id.orEmpty() }
        if (id.isBlank()) return ToolJson.missing("task_id")
        return rt.whatsapp.control(command, id).toJson()
    }

    // ---- shared --------------------------------------------------------------------------

    /** Only one screen task at a time, across Instagram, Facebook and WhatsApp. */
    private fun busyReply(rt: AgentRuntime): JSONObject? =
        if (rt.social.active() != null || rt.whatsapp.active() != null) {
            reply("busy").put("error", "Another screen task is still running. Wait for it, or cancel it first.")
        } else {
            null
        }

    private fun reply(code: String) = JSONObject().put("result", code)

    private fun TaskReport.toJson(): JSONObject = reply(code)
        .put("task_id", taskId)
        .put("status", status.name.lowercase())
        .put("state", state.name.lowercase())
        .put("message", message)
        .put("step", step)
        .put("total_steps", totalSteps)
        .put("accepted", accepted)
        .put("needs_confirmation", needsConfirmation)
        .also { json -> blocker?.let { json.put("blocker", it.name.lowercase()) } }

    private fun WhatsAppReport.toJson(): JSONObject = reply(code)
        .put("task_id", taskId)
        .put("status", status.name.lowercase())
        .put("message", message)
        .put("items", JSONArray(items))
        .put("accepted", accepted)

    /** Built once, on first use. */
    private class AgentRuntime private constructor(context: Context) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val detector = BlockerDetector()
        private val driver = AccessibilityScreenDriver()
        private val executor = UiActionExecutor(driver, blockerCheck = detector::detect)
        private val typer = TextInputExecutor(driver, executor)
        val social = SocialTaskRunner(driver, executor, typer, AndroidAppOpener(context), detector)
        val whatsappAgent = WhatsAppAgent(
            driver, executor, typer, AndroidWhatsAppLauncher(context), WhatsAppNotificationStore, detector,
        )
        val whatsapp = WhatsAppTaskManager(whatsappAgent, scope)

        companion object {
            @Volatile private var instance: AgentRuntime? = null

            fun get(context: Context): AgentRuntime = instance ?: synchronized(this) {
                instance ?: AgentRuntime(context.applicationContext).also { instance = it }
            }
        }
    }
}
