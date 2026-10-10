package com.Lia.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolDeclarationsTest {
    private val byName = AgentTools.declarations().associateBy { it.getString("name") }

    private fun props(name: String) = byName.getValue(name).getJSONObject("parameters").getJSONObject("properties")

    private fun required(name: String) =
        byName.getValue(name).getJSONObject("parameters").getJSONArray("required").let { a -> (0 until a.length()).map { a.getString(it) } }

    @Test fun allFourToolsAreDeclared() {
        assertEquals(
            setOf("execute_social_media_task", "social_media_task_control", "execute_whatsapp_task", "whatsapp_task_control"),
            byName.keys,
        )
        assertEquals(AgentTools.names, byName.keys)
    }

    @Test fun socialTaskTakesTheSixArguments() {
        assertEquals(
            setOf("platform", "action", "media_uri", "caption", "mode", "target_account"),
            props("execute_social_media_task").keys().asSequence().toSet(),
        )
        assertEquals(setOf("platform", "action"), required("execute_social_media_task").toSet())
    }

    @Test fun socialControlSaysConfirmNeedsAnExplicitYes() {
        val description = byName.getValue("social_media_task_control").getString("description")
        assertTrue(description.contains("Only call confirm after the user explicitly said yes"))
        assertEquals(setOf("command", "task_id"), props("social_media_task_control").keys().asSequence().toSet())
        assertEquals(setOf("command"), required("social_media_task_control").toSet())
    }

    @Test fun whatsAppToolsAreDeclared() {
        val keys = props("execute_whatsapp_task").keys().asSequence().toSet()
        assertTrue(keys.containsAll(setOf("action", "phone_number", "contact_name", "message", "query")))
        assertEquals(setOf("command", "task_id"), props("whatsapp_task_control").keys().asSequence().toSet())
    }

    @Test fun everyParameterIsAString() {
        for (name in byName.keys) {
            val p = props(name)
            for (key in p.keys()) assertEquals("$name.$key", "STRING", p.getJSONObject(key).getString("type"))
        }
    }

    @Test fun theCatalogOffersTheNewToolsNextToTheOldOnes() {
        val names = LiaToolCatalog.toolNames()
        assertTrue(names.containsAll(AgentTools.names))
        assertTrue(names.containsAll(listOf("open_app", "read_screen", "tap_text", "go_home")))
    }
}
