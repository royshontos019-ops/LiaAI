package com.Lia.assistant

import android.content.Context
import com.Lia.assistant.action.PhoneActions
import com.Lia.assistant.action.ToolJson
import org.json.JSONObject

/**
 * Play flavor: open_app, call_contact and message_contact only. Messaging opens a composer and
 * the USER taps Send. There is no screen reading or control in this build.
 */
object ActionExecutor {
    val isAvailable: Boolean = false

    suspend fun execute(context: Context, name: String, args: JSONObject): JSONObject = when (name) {
        "open_app" -> PhoneActions.openApp(context, args)
        "call_contact" -> PhoneActions.callContact(context, args)
        "message_contact" -> PhoneActions.messageContact(context, args).json
        else -> ToolJson.unknownTool(name)
    }
}
