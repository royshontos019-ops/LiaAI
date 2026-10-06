package com.Lia.assistant

import com.Lia.assistant.action.PhoneToolDeclarations
import com.Lia.assistant.action.declaredToolNames
import com.Lia.assistant.action.functionDecl
import org.json.JSONArray

/** Direct flavor: phone actions plus screen control. */
object LiaToolCatalog {
    /** Gemini Live `functionDeclarations` for this flavor. */
    fun declarations(): JSONArray {
        val out = JSONArray()
        PhoneToolDeclarations.base(autoSend = true).forEach { out.put(it) }
        out.put(functionDecl("read_screen", "Read the visible text and controls of the app that is on screen."))
        out.put(
            functionDecl(
                "tap_text",
                "Tap the button or text on screen whose label matches.",
                mapOf("text" to "The label to tap, for example Send or Search"),
                listOf("text"),
            ),
        )
        out.put(
            functionDecl(
                "type_text",
                "Type text into the input field that is focused on screen (adds to what is already there).",
                mapOf("text" to "The text to type"),
                listOf("text"),
            ),
        )
        out.put(
            functionDecl(
                "scroll_screen",
                "Scroll the current screen.",
                mapOf("direction" to "up, down, left or right (default down)"),
            ),
        )
        out.put(functionDecl("go_back", "Press the Back button."))
        out.put(functionDecl("go_home", "Go to the home screen."))
        return out
    }

    fun toolNames(): List<String> = declarations().declaredToolNames()
}
