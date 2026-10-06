package com.Lia.assistant

import android.content.Context
import com.Lia.assistant.action.AccessibilityTools
import com.Lia.assistant.action.NovaAccessibilityService
import com.Lia.assistant.action.PhoneActions
import com.Lia.assistant.action.ToolJson
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Direct flavor: phone actions plus screen control. Every tool answers with a JSONObject. */
object ActionExecutor {
    const val AUTO_SEND_TRIES = 12
    const val AUTO_SEND_DELAY_MS = 400L

    val isAvailable: Boolean get() = NovaAccessibilityService.instance != null

    suspend fun execute(context: Context, name: String, args: JSONObject): JSONObject = when (name) {
        "open_app" -> PhoneActions.openApp(context, args)
        "call_contact" -> PhoneActions.callContact(context, args)
        "message_contact" -> messageContact(context, args)
        "read_screen" -> AccessibilityTools.readScreen()
        "tap_text" -> withText(args) { AccessibilityTools.tapText(it) }
        "type_text" -> withText(args) { AccessibilityTools.typeText(it) }
        "scroll_screen" -> AccessibilityTools.scroll(args.optString("direction", "down"))
        "go_back" -> AccessibilityTools.goBack()
        "go_home" -> AccessibilityTools.goHome()
        else -> ToolJson.unknownTool(name)
    }

    private inline fun withText(args: JSONObject, block: (String) -> JSONObject): JSONObject {
        val text = args.optString("text")
        return if (text.isBlank()) ToolJson.missing("text") else block(text)
    }

    /** Opens the composer, then (if the service is on) taps Send. */
    private suspend fun messageContact(context: Context, args: JSONObject): JSONObject {
        val outcome = PhoneActions.messageContact(context, args)
        if (!outcome.composerOpened) return outcome.json
        if (NovaAccessibilityService.instance == null) return outcome.json   // user taps Send
        val expected = outcome.targetPackage ?: return outcome.json           // cannot verify the app

        repeat(AUTO_SEND_TRIES) {
            delay(AUTO_SEND_DELAY_MS)
            // Only touch the screen while the expected messaging app is in front.
            if (!AccessibilityTools.isForeground(expected)) return@repeat
            if (outcome.isWhatsApp) AccessibilityTools.tapText("Continue to chat", allowGestureFallback = false)
            val tapped = AccessibilityTools.tapText("Send", allowGestureFallback = false)
            if (tapped.optString("result") == "tapped") return ToolJson.result("sent")
        }
        return ToolJson.result("composer_opened_send_not_found")
    }
}
