package com.Lia.assistant

import org.json.JSONObject

object ActionExecutor {
    val isAvailable: Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun execute(action: String, args: JSONObject = JSONObject()): ToolResult =
        ToolResult.unavailable("Screen control")
}
