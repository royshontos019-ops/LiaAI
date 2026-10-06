package com.Lia.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolDeclarationsTest {
    @Test fun allParamsAreStringsAndSchemaIsObject() {
        val arr = ToolDeclarations.from(
            listOf(ToolSpec("t", "desc", listOf(ToolParam("a", "first"), ToolParam("b", "second", required = false)))),
        )
        val params = arr.getJSONObject(0).getJSONObject("parameters")
        assertEquals("OBJECT", params.getString("type"))
        val props = params.getJSONObject("properties")
        assertEquals("STRING", props.getJSONObject("a").getString("type"))
        assertEquals("STRING", props.getJSONObject("b").getString("type"))
        assertEquals(1, params.getJSONArray("required").length())
    }

    @Test fun toolWithoutParamsHasNoParametersBlock() {
        val arr = ToolDeclarations.from(listOf(ToolSpec("ping", "no args")))
        assertFalse(arr.getJSONObject(0).has("parameters"))
    }

    @Test fun emptyListGivesEmptyArray() = assertTrue(ToolDeclarations.from(emptyList()).length() == 0)
}
