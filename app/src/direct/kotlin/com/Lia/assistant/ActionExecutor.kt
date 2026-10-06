package com.Lia.assistant

import android.accessibilityservice.AccessibilityService
import org.json.JSONObject

object ActionExecutor {
    val isAvailable: Boolean get() = LiaAccessibilityService.instance != null

    @Suppress("UNUSED_PARAMETER")
    fun execute(action: String, args: JSONObject = JSONObject()): ToolResult {
        val svc = LiaAccessibilityService.instance
            ?: return ToolResult(false, "Accessibility service is not enabled.")
        val done = when (action) {
            "back" -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            "home" -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            "recents" -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            else -> return ToolResult(false, "Unknown action: $action")
        }
        return ToolResult(done, if (done) "ok" else "action failed")
    }
}
