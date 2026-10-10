package com.Lia.assistant

import android.content.ContextWrapper
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class PlayFlavorTest {
    private val context = ContextWrapper(null)

    private fun run(name: String, args: JSONObject = JSONObject()): JSONObject =
        runBlocking { ActionExecutor.execute(context, name, args) }

    @Test fun play_declaresExactlyThreeTools() {
        assertEquals("play", FlavorRoutes.FLAVOR_NAME)
        assertEquals(listOf("open_app", "call_contact", "message_contact", "build_website"), LiaToolCatalog.toolNames())
        assertFalse(ActionExecutor.isAvailable)
    }

    @Test fun play_everyParameterIsAString() {
        val decls = LiaToolCatalog.declarations()
        for (i in 0 until decls.length()) {
            val props = decls.getJSONObject(i).getJSONObject("parameters").getJSONObject("properties")
            for (key in props.keys()) assertEquals("STRING", props.getJSONObject(key).getString("type"))
        }
    }

    @Test fun screenToolsDoNotExistInThePlayBuild() {
        for (tool in listOf("read_screen", "tap_text", "type_text", "scroll_screen", "go_back", "go_home")) {
            assertEquals("Unknown tool: $tool", run(tool).getString("error"))
        }
    }

    @Test fun unknownToolReturnsAnError() =
        assertEquals("Unknown tool: nope", run("nope").getString("error"))

    @Test fun phoneToolsValidateTheirArguments() {
        assertEquals("Missing parameter: app_name", run("open_app").getString("error"))
        assertEquals("Missing parameter: contact", run("call_contact").getString("error"))
        assertEquals(
            "Missing parameter: message",
            run("message_contact", JSONObject().put("contact", "Mom")).getString("error"),
        )
    }

    @Test fun promptSaysPlainlyWhatTheAssistantCannotDo() {
        val s = LiaCapabilityPrompts.toolInstruction("Lia")
        assertTrue(s.contains("cannot read or control the screen"))
        assertTrue(s.contains("cannot post to social apps"))
        assertTrue(s.contains("taps Send") || s.contains("tap Send") || s.contains("user taps Send"))
    }
}
