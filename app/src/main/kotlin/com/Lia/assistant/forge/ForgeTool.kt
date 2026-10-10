package com.Lia.assistant.forge

import com.Lia.assistant.action.functionDecl
import org.json.JSONObject

/** The `build_website` tool, declared the same way in both flavors. */
object ForgeTool {
    const val NAME = "build_website"

    fun declaration(): JSONObject = functionDecl(
        NAME,
        "Build a complete website for the user. It starts at once and takes a minute or two; the finished " +
            "site appears in Lia's Forge screen. Tell the user it has started. Do not read the code out.",
        mapOf("prompt" to "What the website is for and anything the user wants on it (name, topic, style, sections)"),
        listOf("prompt"),
    )
}
