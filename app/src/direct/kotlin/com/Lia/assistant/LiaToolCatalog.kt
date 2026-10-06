package com.Lia.assistant

import org.json.JSONArray

object LiaToolCatalog {
    fun tools(): List<ToolSpec> = listOf(
        ToolSpec(
            name = "device_action",
            description = "Perform a global device action on the phone: back, home or recents.",
            params = listOf(ToolParam("action", "One of: back, home, recents")),
        ),
    )

    /** Gemini Live `functionDeclarations` for this flavor. */
    fun declarations(): JSONArray = ToolDeclarations.from(tools())
}
