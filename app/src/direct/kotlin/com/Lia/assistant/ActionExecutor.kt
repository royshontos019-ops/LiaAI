package com.Lia.assistant

import android.accessibilityservice.AccessibilityService
import org.json.JSONObject

object ActionExecutor {
    val isAvailable: Boolean get() = LiaAccessibilityService.instance != null

    /**
     * [action] is either a plain action ("back", "home", "recents") or the tool name
     * "device_action", in which case the real action is read from args.action.
     */
    fun execute(action: String, args: JSONObject = JSONObject()): ToolResult {
        val svc = LiaAccessibilityService.instance
            ?: return ToolResult(false, "Accessibility service is not enabled.")
        val name = if (action == "device_action") args.optString("action").trim().lowercase() else action
        val done = when (name) {
            "back" -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            "home" -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            "recents" -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            else -> return ToolResult(false, "Unknown action: $name")
        }
        return ToolResult(done, if (done) "ok" else "action failed")
    }
}
