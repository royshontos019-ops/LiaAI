package com.Lia.assistant

import org.json.JSONObject

object LiaToolCatalog {
    fun tools(): List<ToolSpec> = listOf(
        ToolSpec(
            name = "device_action",
            description = "Perform a global device action: back, home or recents.",
            parametersSchema = JSONObject(
                """{"type":"object","properties":{"action":{"type":"string","enum":["back","home","recents"]}},"required":["action"]}"""
            ),
        ),
    )
}
