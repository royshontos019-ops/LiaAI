package com.Lia.assistant.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FunctionDeclTest {
    @Test fun allParamsAreStringsInsideAnObjectSchema() {
        val d = functionDecl(
            "send", "Send it",
            mapOf("to" to "who", "body" to "what", "app" to "which"),
            listOf("to", "body"),
        )
        assertEquals("send", d.getString("name"))
        val schema = d.getJSONObject("parameters")
        assertEquals("OBJECT", schema.getString("type"))
        val props = schema.getJSONObject("properties")
        for (name in listOf("to", "body", "app")) assertEquals("STRING", props.getJSONObject(name).getString("type"))
        assertEquals(2, schema.getJSONArray("required").length())
    }

    @Test fun noParamsMeansNoParametersBlock() =
        assertFalse(functionDecl("go_home", "Home").has("parameters"))

    @Test fun requiredNamesNotInParamsAreDropped() {
        val d = functionDecl("t", "d", mapOf("a" to "x"), listOf("a", "ghost"))
        assertEquals(1, d.getJSONObject("parameters").getJSONArray("required").length())
    }

    @Test fun noRequiredMeansNoRequiredList() {
        val d = functionDecl("scroll", "d", mapOf("direction" to "x"))
        assertFalse(d.getJSONObject("parameters").has("required"))
    }

    @Test fun baseToolsDifferOnlyInTheMessageDescription() {
        val auto = PhoneToolDeclarations.base(autoSend = true)
        val manual = PhoneToolDeclarations.base(autoSend = false)
        assertEquals(listOf("open_app", "call_contact", "message_contact"), auto.map { it.getString("name") })
        assertEquals(auto.map { it.getString("name") }, manual.map { it.getString("name") })
        assertTrue(manual[2].getString("description").contains("taps Send themselves"))
    }

    @Test fun toolJsonShapes() {
        assertEquals("Unknown tool: nope", ToolJson.unknownTool("nope").getString("error"))
        assertEquals("Missing parameter: text", ToolJson.missing("text").getString("error"))
        assertEquals("opened", ToolJson.result("opened", "app" to "maps").getString("result"))
        assertEquals("maps", ToolJson.result("opened", "app" to "maps").getString("app"))
    }
}
