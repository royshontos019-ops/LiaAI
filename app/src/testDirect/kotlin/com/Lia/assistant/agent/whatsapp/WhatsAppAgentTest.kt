package com.Lia.assistant.agent.whatsapp

import com.Lia.assistant.agent.FakeScreen
import com.Lia.assistant.agent.FakeScreenDriver
import com.Lia.assistant.agent.node
import com.Lia.assistant.agent.core.ActionTimings
import com.Lia.assistant.agent.core.ScreenObserver
import com.Lia.assistant.agent.core.TargetResolver
import com.Lia.assistant.agent.core.TextInputExecutor
import com.Lia.assistant.agent.core.UiActionExecutor
import com.Lia.assistant.agent.social.BlockerDetector
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val WA = "com.whatsapp"
private const val ROW = "$WA:id/conversations_row_contact_name"
private const val MSG = "$WA:id/message_text"

private fun row(index: Int, name: String) = node(index, name, id = ROW, clickable = true)

private fun searchScreen(vararg names: String, onClick: Map<String, String> = emptyMap()) = FakeScreen(
    WA,
    listOf(node(0, id = "$WA:id/search_input", cls = "android.widget.EditText", editable = true, focused = true)) +
        names.mapIndexed { i, n -> row(i + 1, n) },
    onClick = onClick,
)

private fun world(searching: FakeScreen): Map<String, FakeScreen> = mapOf(
    "home" to FakeScreen(
        WA,
        listOf(node(0, desc = "Search", id = "$WA:id/menuitem_search", clickable = true)),
        onClick = mapOf("Search" to "searching"),
    ),
    "searching" to searching,
    "chat" to FakeScreen(
        WA,
        listOf(
            node(0, "Hi", id = MSG),
            node(1, "How are you?", id = MSG),
            node(2, "See you tomorrow", id = MSG),
            node(3, id = "$WA:id/entry", cls = "android.widget.EditText", editable = true, focused = true),
        ),
    ),
    "chatWithDraft" to FakeScreen(
        WA,
        listOf(
            node(0, "Running late, sorry", id = "$WA:id/entry", cls = "android.widget.EditText", editable = true, focused = true),
            node(1, desc = "Send", id = "$WA:id/send", clickable = true),
        ),
        onClick = mapOf("Send" to "chatSent"),
    ),
    "chatSent" to FakeScreen(
        WA,
        listOf(
            node(0, id = "$WA:id/entry", cls = "android.widget.EditText", editable = true, focused = true),
            node(1, desc = "Voice message", id = "$WA:id/voice_note_btn", clickable = true),
        ),
    ),
    "notOnWhatsApp" to FakeScreen(WA, listOf(node(0, "Phone number shared via url is invalid."), node(1, "OK", clickable = true))),
)

private class FakeLauncher(private val driver: FakeScreenDriver, private val chatScreen: String, private val ok: Boolean = true) : WhatsAppLauncher {
    val chats = mutableListOf<Pair<String, String?>>()
    var appOpens = 0

    override suspend fun openChat(phone: String, prefill: String?): Boolean {
        chats += phone to prefill
        if (!ok) return false
        driver.current = chatScreen
        return true
    }

    override suspend fun openApp(): Boolean {
        appOpens++
        if (!ok) return false
        driver.current = "home"
        return true
    }
}

private class FakeUnread(override val isAvailable: Boolean, private val chats: List<UnreadChat> = emptyList()) : UnreadMessageSource {
    override fun unread(): List<UnreadChat> = chats
}

private class Harness(
    screens: Map<String, FakeScreen>,
    chatScreen: String = "chat",
    unread: UnreadMessageSource = FakeUnread(true),
) {
    val driver = FakeScreenDriver(screens, "home")
    val launcher = FakeLauncher(driver, chatScreen)
    private val detector = BlockerDetector()
    private val timings = ActionTimings(
        resolveAttempts = 2, resolveIntervalMs = 0, settleMs = 0,
        verifyAttempts = 2, verifyIntervalMs = 0, maxStaleRetries = 2,
    )
    private val noPause: suspend (Long) -> Unit = {}
    private val executor = UiActionExecutor(
        driver = driver, observer = ScreenObserver(), resolver = TargetResolver(),
        blockerCheck = detector::detect, timings = timings, pause = noPause,
    )
    private val typer = TextInputExecutor(driver, executor, timings, noPause)
    val agent = WhatsAppAgent(driver, executor, typer, launcher, unread, detector, noPause)
}

private fun req(
    action: WhatsAppAction,
    phone: String? = null,
    name: String? = null,
    message: String = "",
    query: String = "",
) = WhatsAppRequest(action, phone, name, message, query, 10)

class WhatsAppAgentTest {

    // ---- READ_UNREAD never opens the app -------------------------------------------------

    @Test fun readUnreadUsesOnlyTheNotificationSource() = runTest {
        val unread = FakeUnread(true, listOf(UnreadChat("Rahim", "Where are you?", 1), UnreadChat("Family", "3 new messages", 3)))
        val h = Harness(world(searchScreen()), unread = unread)

        val out = h.agent.run(req(WhatsAppAction.READ_UNREAD))
        assertEquals(WhatsAppStatus.COMPLETED, out.status)
        assertEquals("unread_read", out.code)
        assertEquals(listOf("Rahim: Where are you?", "Family (3): 3 new messages"), out.items)
        assertTrue("the screen was never touched", h.driver.log.isEmpty())
        assertEquals("WhatsApp was never opened", 0, h.launcher.appOpens)
        assertTrue(h.launcher.chats.isEmpty())
    }

    @Test fun readUnreadWithNoMessagesSaysSo() = runTest {
        val h = Harness(world(searchScreen()))
        val out = h.agent.run(req(WhatsAppAction.READ_UNREAD))
        assertEquals("no_unread", out.code)
        assertTrue(out.items.isEmpty())
    }

    @Test fun readUnreadWithoutNotificationAccessFailsAndStillDoesNotOpenWhatsApp() = runTest {
        val h = Harness(world(searchScreen()), unread = FakeUnread(false))
        val out = h.agent.run(req(WhatsAppAction.READ_UNREAD))
        assertEquals(WhatsAppStatus.FAILED, out.status)
        assertEquals("notification_access_off", out.code)
        assertEquals(0, h.launcher.appOpens)
        assertTrue(h.driver.log.isEmpty())
    }

    // ---- SEND_MESSAGE goes straight to the chat ------------------------------------------

    @Test fun sendMessageOpensTheChatByPhoneNumberAndTapsSend() = runTest {
        val h = Harness(world(searchScreen()), chatScreen = "chatWithDraft")
        val out = h.agent.run(req(WhatsAppAction.SEND_MESSAGE, phone = "8801712345678", message = "Running late, sorry"))

        assertEquals(WhatsAppStatus.COMPLETED, out.status)
        assertEquals("sent", out.code)
        assertEquals(listOf("8801712345678" to "Running late, sorry"), h.launcher.chats)
        assertEquals("no searching: it went straight to the chat", 0, h.launcher.appOpens)
        assertEquals(1, h.driver.clickCount("Send"))
    }

    @Test fun aNumberThatIsNotOnWhatsAppFailsWithoutTapping() = runTest {
        val h = Harness(world(searchScreen()), chatScreen = "notOnWhatsApp")
        val out = h.agent.run(req(WhatsAppAction.SEND_MESSAGE, phone = "8801000000000", message = "hi"))
        assertEquals(WhatsAppStatus.FAILED, out.status)
        assertEquals("not_on_whatsapp", out.code)
        assertTrue(h.driver.log.none { it.startsWith("click:") })
    }

    @Test fun aSendTapThatCannotBeVerifiedIsNotCalledSent() = runTest {
        val h = Harness(world(searchScreen()), chatScreen = "chatWithDraft")
        h.driver.noEffectLabels += "Send"
        val out = h.agent.run(req(WhatsAppAction.SEND_MESSAGE, phone = "8801712345678", message = "Running late, sorry"))
        assertEquals("sent_unverified", out.code)
        assertTrue(out.message.contains("check"))
        assertEquals("never tap Send twice", 1, h.driver.clickCount("Send"))
    }

    // ---- ambiguity ------------------------------------------------------------------------

    @Test fun twoMatchingContactsFailWithContactAmbiguousAndNothingIsOpened() = runTest {
        val screens = world(searchScreen("Rahim Khan", "Rahim Uddin", onClick = mapOf("Rahim Khan" to "chat", "Rahim Uddin" to "chat")))
        val h = Harness(screens)

        val out = h.agent.run(req(WhatsAppAction.READ_CHAT, name = "Rahim"))
        assertEquals(WhatsAppStatus.FAILED, out.status)
        assertEquals("contact_ambiguous", out.code)
        assertEquals(listOf("Rahim Khan", "Rahim Uddin"), out.items)
        assertTrue("it must not pick one", h.driver.log.none { it.startsWith("click:Rahim") })
        assertEquals("chat was never opened", "searching", h.driver.current)
    }

    @Test fun everyNameBasedActionRefusesToGuess() = runTest {
        for (action in listOf(WhatsAppAction.READ_CHAT, WhatsAppAction.MUTE_CHAT, WhatsAppAction.UNMUTE_CHAT, WhatsAppAction.MARK_READ)) {
            val h = Harness(world(searchScreen("Rahim Khan", "Rahim Uddin")))
            val out = h.agent.run(req(action, name = "Rahim"))
            assertEquals("$action", "contact_ambiguous", out.code)
            assertFalse("$action", h.driver.clicked("Rahim Khan") || h.driver.clicked("Rahim Uddin"))
        }
    }

    @Test fun aSingleMatchOpensTheChatAndReadsIt() = runTest {
        val h = Harness(world(searchScreen("Rahim Khan", "Karim", onClick = mapOf("Rahim Khan" to "chat"))))
        val out = h.agent.run(req(WhatsAppAction.READ_CHAT, name = "Rahim"))
        assertEquals(WhatsAppStatus.COMPLETED, out.status)
        assertEquals("chat_read", out.code)
        assertEquals(listOf("Hi", "How are you?", "See you tomorrow"), out.items)
        assertTrue(h.driver.clicked("Rahim Khan"))
    }

    @Test fun anExactNameBeatsLongerNamesThatMerelyContainIt() = runTest {
        val h = Harness(world(searchScreen("Rahim", "Rahim Khan", onClick = mapOf("Rahim" to "chat"))))
        val out = h.agent.run(req(WhatsAppAction.READ_CHAT, name = "rahim"))
        assertEquals("chat_read", out.code)
        assertTrue(h.driver.clicked("Rahim"))
        assertFalse(h.driver.clicked("Rahim Khan"))
    }

    @Test fun anUnknownNameIsNotFound() = runTest {
        val h = Harness(world(searchScreen("Karim")))
        val out = h.agent.run(req(WhatsAppAction.READ_CHAT, name = "Rahim"))
        assertEquals("contact_not_found", out.code)
    }

    @Test fun searchChatListsEveryMatchWithoutChoosing() = runTest {
        val h = Harness(world(searchScreen("Rahim Khan", "Rahim Uddin")))
        val out = h.agent.run(req(WhatsAppAction.SEARCH_CHAT, query = "Rahim"))
        assertEquals("matches_found", out.code)
        assertEquals(listOf("Rahim Khan", "Rahim Uddin"), out.items)
        assertTrue(h.driver.log.none { it.startsWith("click:Rahim") })
    }

    @Test fun markReadByPhoneOpensTheChatThenGoesBack() = runTest {
        val h = Harness(world(searchScreen()))
        val out = h.agent.run(req(WhatsAppAction.MARK_READ, phone = "8801712345678"))
        assertEquals("marked_read", out.code)
        assertEquals("8801712345678" to null, h.launcher.chats.single())
        assertTrue(h.driver.log.contains("back"))
    }

    // ---- parsing --------------------------------------------------------------------------

    private fun parse(vararg pairs: Pair<String, String?>) = WhatsAppRequest.parse(mapOf(*pairs))

    private fun invalid(result: WhatsAppParse): WhatsAppParse.Invalid {
        assertTrue("expected Invalid but was $result", result is WhatsAppParse.Invalid)
        return result as WhatsAppParse.Invalid
    }

    @Test fun phoneNumbersAreNormalisedAndLocalOnesAreRefused() {
        val ok = parse("action" to "send_message", "phone_number" to "+880 1712-345678", "message" to "hi") as WhatsAppParse.Valid
        assertEquals("8801712345678", ok.request.phone)
        val double0 = parse("action" to "send_message", "phone_number" to "00880 1712 345678", "message" to "hi") as WhatsAppParse.Valid
        assertEquals("8801712345678", double0.request.phone)
        assertEquals("phone_needs_country_code", invalid(parse("action" to "send_message", "phone_number" to "01712345678", "message" to "hi")).code)
        assertEquals("phone_invalid", invalid(parse("action" to "send_message", "phone_number" to "12345", "message" to "hi")).code)
    }

    @Test fun eachActionChecksItsOwnArguments() {
        assertEquals("missing_action", invalid(parse()).code)
        assertEquals("unknown_action", invalid(parse("action" to "delete_chat")).code)
        assertEquals("phone_number_required", invalid(parse("action" to "send_message", "contact_name" to "Rahim", "message" to "hi")).code)
        assertEquals("message_required", invalid(parse("action" to "send_message", "phone_number" to "+8801712345678")).code)
        assertEquals("query_required", invalid(parse("action" to "search_chat")).code)
        assertEquals("chat_required", invalid(parse("action" to "mute_chat")).code)
        assertTrue(parse("action" to "read_unread") is WhatsAppParse.Valid)
        assertTrue(parse("action" to "mute_chat", "contact_name" to "Rahim") is WhatsAppParse.Valid)
    }

    @Test fun limitIsKeptInRange() {
        val big = parse("action" to "read_unread", "limit" to "999") as WhatsAppParse.Valid
        assertEquals(WhatsAppRequest.MAX_LIMIT, big.request.limit)
        val none = parse("action" to "read_unread") as WhatsAppParse.Valid
        assertEquals(WhatsAppRequest.DEFAULT_LIMIT, none.request.limit)
    }
}
