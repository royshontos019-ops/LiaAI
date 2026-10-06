package com.Lia.assistant

import android.content.ContextWrapper
import com.Lia.assistant.action.ScreenFormatter
import com.Lia.assistant.action.UiNode
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class DirectFlavorTest {
    private val context = ContextWrapper(null)

    private fun run(name: String, args: JSONObject = JSONObject()): JSONObject =
        runBlocking { ActionExecutor.execute(context, name, args) }

    @Test fun direct_declaresTheNineTools() {
        assertEquals("direct", FlavorRoutes.FLAVOR_NAME)
        assertEquals(
            listOf(
                "open_app", "call_contact", "message_contact",
                "read_screen", "tap_text", "type_text", "scroll_screen", "go_back", "go_home",
            ),
            LiaToolCatalog.toolNames(),
        )
    }

    @Test fun direct_everyParameterIsAString() {
        val decls = LiaToolCatalog.declarations()
        for (i in 0 until decls.length()) {
            val params = decls.getJSONObject(i).optJSONObject("parameters") ?: continue
            assertEquals("OBJECT", params.getString("type"))
            val props = params.getJSONObject("properties")
            for (key in props.keys()) assertEquals("STRING", props.getJSONObject(key).getString("type"))
        }
    }

    @Test fun unknownToolReturnsAnError() =
        assertEquals("Unknown tool: fly_to_moon", run("fly_to_moon").getString("error"))

    @Test fun oldToolNameIsNoLongerKnown() =
        assertEquals("Unknown tool: device_action", run("device_action").getString("error"))

    @Test fun screenToolsReportAccessibilityNotEnabledWithoutTheService() {
        assertFalse(ActionExecutor.isAvailable)
        val withText = JSONObject().put("text", "Send")
        assertEquals("accessibility_not_enabled", run("read_screen").getString("result"))
        assertEquals("accessibility_not_enabled", run("tap_text", withText).getString("result"))
        assertEquals("accessibility_not_enabled", run("type_text", withText).getString("result"))
        assertEquals("accessibility_not_enabled", run("scroll_screen").getString("result"))
        assertEquals("accessibility_not_enabled", run("go_back").getString("result"))
        assertEquals("accessibility_not_enabled", run("go_home").getString("result"))
    }

    @Test fun tapAndTypeNeedText() {
        assertEquals("Missing parameter: text", run("tap_text").getString("error"))
        assertEquals("Missing parameter: text", run("type_text", JSONObject().put("text", "  ")).getString("error"))
    }

    @Test fun phoneToolsValidateTheirArguments() {
        assertEquals("Missing parameter: app_name", run("open_app").getString("error"))
        assertEquals("Missing parameter: contact", run("call_contact").getString("error"))
        assertEquals("Missing parameter: contact", run("message_contact").getString("error"))
        assertEquals(
            "Missing parameter: message",
            run("message_contact", JSONObject().put("contact", "Mom")).getString("error"),
        )
    }

    @Test fun promptMentionsTheScreenTools() =
        assertTrue(LiaCapabilityPrompts.toolInstruction("Lia").contains("read_screen"))

    // ---- screen formatting through a fake node tree ----
    private class Node(
        override val text: String? = null,
        override val contentDescription: String? = null,
        override val hintText: String? = null,
        override val isEditable: Boolean = false,
        override val isClickable: Boolean = false,
        override val isPassword: Boolean = false,
        private val kids: List<Node> = emptyList(),
    ) : UiNode {
        override val childCount: Int get() = kids.size
        override fun child(index: Int): UiNode? = kids.getOrNull(index)
    }

    @Test fun formatterLabelsKindsAndSkipsBlanksAndPasswords() {
        val root = Node(
            kids = listOf(
                Node(text = "Search", isEditable = true),
                Node(text = "Send", isClickable = true),
                Node(text = "Hello there"),
                Node(text = "   "),
                Node(text = "hunter2", isEditable = true, isPassword = true),
                Node(contentDescription = "Menu", isClickable = true),
                Node(hintText = "Type a message", isEditable = true),
            ),
        )
        assertEquals(
            listOf("[input] Search", "[button] Send", "[text] Hello there", "[button] Menu", "[input] Type a message"),
            ScreenFormatter.format(root),
        )
    }

    @Test fun formatterIsBreadthFirstAndUnique() {
        val root = Node(
            kids = listOf(
                Node(text = "A", kids = listOf(Node(text = "A1"))),
                Node(text = "B", kids = listOf(Node(text = "B1"))),
                Node(text = "A"),
            ),
        )
        assertEquals(listOf("[text] A", "[text] B", "[text] A1", "[text] B1"), ScreenFormatter.format(root))
    }

    @Test fun formatterCapsElementsAndLabelLength() {
        val many = Node(kids = (1..100).map { Node(text = "item $it") })
        assertEquals(40, ScreenFormatter.format(many).size)
        assertEquals(5, ScreenFormatter.format(many, maxElements = 5).size)

        val long = Node(text = "word ".repeat(30))
        val line = ScreenFormatter.format(long, maxLabelLength = 40).single()
        assertEquals("[text] ".length + 40, line.length)
        assertTrue(line.endsWith("…"))
    }

    @Test fun formatterCollapsesWhitespace() =
        assertEquals(listOf("[text] a b c"), ScreenFormatter.format(Node(text = " a \n b\t c ")))

    @Test fun textMatchScores() {
        assertEquals(3, com.Lia.assistant.action.TextMatch.score("Send", "send"))
        assertEquals(2, com.Lia.assistant.action.TextMatch.score("Send message", "send"))
        assertEquals(1, com.Lia.assistant.action.TextMatch.score("Resend", "send"))
        assertEquals(0, com.Lia.assistant.action.TextMatch.score("Cancel", "send"))
        assertEquals(0, com.Lia.assistant.action.TextMatch.score(null, "send"))
    }
}
