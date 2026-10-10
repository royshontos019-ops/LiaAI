package com.Lia.assistant.agent.whatsapp

import com.Lia.assistant.agent.core.ActionResult
import com.Lia.assistant.agent.core.Expectation
import com.Lia.assistant.agent.core.FailureReason
import com.Lia.assistant.agent.core.ScreenDriver
import com.Lia.assistant.agent.core.ScreenObservation
import com.Lia.assistant.agent.core.TextInputExecutor
import com.Lia.assistant.agent.core.UiActionExecutor
import com.Lia.assistant.agent.social.BlockerDetector
import kotlinx.coroutines.delay

/**
 * Does one WhatsApp action. Rules:
 * - READ_UNREAD only reads what the notification listener saw. It never opens WhatsApp.
 * - SEND_MESSAGE goes straight to the chat by phone number.
 * - A chat found by name must match exactly one contact. Two matches fail with "contact_ambiguous".
 */
class WhatsAppAgent(
    private val driver: ScreenDriver,
    private val executor: UiActionExecutor,
    private val typer: TextInputExecutor,
    private val launcher: WhatsAppLauncher,
    private val unread: UnreadMessageSource,
    private val detector: BlockerDetector = BlockerDetector(),
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {

    suspend fun run(request: WhatsAppRequest): WhatsAppOutcome = when (request.action) {
        WhatsAppAction.READ_UNREAD -> readUnread(request)
        WhatsAppAction.SEND_MESSAGE -> sendMessage(request)
        WhatsAppAction.READ_CHAT -> readChat(request)
        WhatsAppAction.SEARCH_CHAT -> searchChat(request)
        WhatsAppAction.MUTE_CHAT -> muteChat(request, mute = true)
        WhatsAppAction.UNMUTE_CHAT -> muteChat(request, mute = false)
        WhatsAppAction.MARK_READ -> markRead(request)
    }

    // ---- actions -------------------------------------------------------------------------

    private fun readUnread(request: WhatsAppRequest): WhatsAppOutcome {
        if (!unread.isAvailable) {
            return failed("notification_access_off", "Notification access is off for Lia. The user can turn it on in Settings, then I can read unread messages without opening WhatsApp.")
        }
        val chats = unread.unread().take(request.limit)
        if (chats.isEmpty()) return done("no_unread", "There are no unread WhatsApp messages.")
        val items = chats.map { c ->
            val count = if (c.count > 1) " (${c.count})" else ""
            "${c.sender}$count: ${c.preview}"
        }
        return done("unread_read", "${chats.size} chat(s) with unread messages.", items)
    }

    private suspend fun sendMessage(request: WhatsAppRequest): WhatsAppOutcome {
        val phone = request.phone ?: return failed("phone_number_required", "A phone number is needed to send.")
        if (!launcher.openChat(phone, request.message)) return failed("whatsapp_not_available", "Could not open WhatsApp. Is it installed?")
        pause(1200)

        val screen = settle() ?: return failed(FailureReason.NO_SCREEN.code, "Could not read the screen.")
        detector.detect(screen)?.let { return blocked(it) }
        if (notOnWhatsApp(screen)) return failed("not_on_whatsapp", "That number is not on WhatsApp.")

        // The text normally arrives prefilled. If it did not, type it.
        if (!screen.containsText(request.message.take(40))) {
            val typed = typer.typeInto(WhatsAppSelectors.chatInput, request.message, WhatsAppSelectors.PACKAGE)
            if (typed !is ActionResult.Success) return fromResult(typed, "sent", "")
        }

        // When the box is empty again the Send button turns into the microphone, so "gone" means sent.
        val tapped = executor.tap(
            WhatsAppSelectors.send,
            Expectation.TargetGone(WhatsAppSelectors.send),
            WhatsAppSelectors.PACKAGE,
        )
        if (tapped is ActionResult.Failed && tapped.reason == FailureReason.VERIFICATION_FAILED) {
            return done("sent_unverified", "I tapped Send but could not confirm the message left. Tell the user to check the chat before sending again.")
        }
        return fromResult(tapped, "sent", "Message sent.")
    }

    private suspend fun readChat(request: WhatsAppRequest): WhatsAppOutcome {
        openChat(request)?.let { return it }
        val screen = settle() ?: return failed(FailureReason.NO_SCREEN.code, "Could not read the screen.")
        detector.detect(screen)?.let { return blocked(it) }
        val messages = screen.elements
            .filter { it.viewId.contains(WhatsAppSelectors.MESSAGE_TEXT_ID) && it.text.isNotBlank() }
            .sortedBy { it.bounds.top }
            .map { it.text }
            .takeLast(request.limit)
        if (messages.isEmpty()) return done("chat_empty", "No messages are visible in this chat.")
        return done("chat_read", "${messages.size} message(s), oldest first.", messages)
    }

    private suspend fun searchChat(request: WhatsAppRequest): WhatsAppOutcome {
        searchFor(request.query)?.let { return it }
        val screen = settle() ?: return failed(FailureReason.NO_SCREEN.code, "Could not read the screen.")
        detector.detect(screen)?.let { return blocked(it) }
        val names = contactRows(screen)
        if (names.isEmpty()) return done("no_matches", "No chats match \"${request.query}\".")
        return done("matches_found", "${names.size} chat(s) match.", names)
    }

    private suspend fun muteChat(request: WhatsAppRequest, mute: Boolean): WhatsAppOutcome {
        openChat(request)?.let { return it }
        val menu = executor.tap(
            WhatsAppSelectors.moreOptions,
            Expectation.AnyOf(
                listOf(
                    Expectation.TargetPresent(WhatsAppSelectors.mute),
                    Expectation.TargetPresent(WhatsAppSelectors.unmute),
                ),
            ),
            WhatsAppSelectors.PACKAGE,
        )
        if (menu !is ActionResult.Success) return fromResult(menu, "", "")

        val screen = menu.observation
        val alreadyMuted = screen != null && screen.elements.any { it.label.equals("Unmute notifications", true) }
        val alreadyUnmuted = screen != null && screen.elements.any { it.label.equals("Mute notifications", true) }
        if (mute && alreadyMuted) {
            driver.back()
            return done("already_muted", "This chat was already muted.")
        }
        if (!mute && alreadyUnmuted) {
            driver.back()
            return done("already_unmuted", "This chat was not muted.")
        }

        if (!mute) {
            val r = executor.tap(WhatsAppSelectors.unmute, Expectation.ScreenChanged, WhatsAppSelectors.PACKAGE)
            return fromResult(r, "unmuted", "Notifications are back on for this chat.")
        }
        val first = executor.tap(WhatsAppSelectors.mute, Expectation.ScreenChanged, WhatsAppSelectors.PACKAGE)
        if (first !is ActionResult.Success) return fromResult(first, "", "")
        val always = executor.tap(WhatsAppSelectors.muteAlways, Expectation.ScreenChanged, WhatsAppSelectors.PACKAGE)
        if (always !is ActionResult.Success) return fromResult(always, "", "")
        val ok = executor.tap(WhatsAppSelectors.ok, Expectation.TargetGone(WhatsAppSelectors.ok), WhatsAppSelectors.PACKAGE)
        return fromResult(ok, "muted", "This chat is muted.")
    }

    private suspend fun markRead(request: WhatsAppRequest): WhatsAppOutcome {
        openChat(request)?.let { return it }
        // Opening a chat marks it read. Then go back to the list.
        driver.back()
        return done("marked_read", "The chat was opened, so it is marked as read.")
    }

    // ---- finding a chat ------------------------------------------------------------------

    /** Opens the chat for [request], or returns the reason it could not. Null means the chat is open. */
    private suspend fun openChat(request: WhatsAppRequest): WhatsAppOutcome? {
        val phone = request.phone
        if (phone != null) {
            if (!launcher.openChat(phone, null)) return failed("whatsapp_not_available", "Could not open WhatsApp. Is it installed?")
            pause(1200)
            val screen = settle() ?: return failed(FailureReason.NO_SCREEN.code, "Could not read the screen.")
            detector.detect(screen)?.let { return blocked(it) }
            if (notOnWhatsApp(screen)) return failed("not_on_whatsapp", "That number is not on WhatsApp.")
            return null
        }

        val name = request.contactName ?: return failed("chat_required", "Which chat?")
        searchFor(name)?.let { return it }
        val screen = settle() ?: return failed(FailureReason.NO_SCREEN.code, "Could not read the screen.")
        detector.detect(screen)?.let { return blocked(it) }

        val candidates = contactRows(screen).filter { it.contains(name, ignoreCase = true) }
        if (candidates.isEmpty()) return failed("contact_not_found", "No chat matches \"$name\".")
        val exact = candidates.filter { it.equals(name, ignoreCase = true) }
        val chosen = when {
            candidates.size == 1 -> candidates.first()
            exact.size == 1 -> exact.first()
            else -> return failed(
                "contact_ambiguous",
                "More than one contact matches \"$name\": ${candidates.joinToString(", ")}. Ask the user which one, or for the phone number.",
            ).copy(items = candidates)
        }
        val opened = executor.tap(WhatsAppSelectors.contactRow(chosen), Expectation.ScreenChanged, WhatsAppSelectors.PACKAGE)
        return if (opened is ActionResult.Success) null else fromResult(opened, "", "")
    }

    /** Opens WhatsApp search and types [text]. Null means the results are showing. */
    private suspend fun searchFor(text: String): WhatsAppOutcome? {
        if (!launcher.openApp()) return failed("whatsapp_not_available", "Could not open WhatsApp. Is it installed?")
        pause(1200)
        val home = settle() ?: return failed(FailureReason.NO_SCREEN.code, "Could not read the screen.")
        detector.detect(home)?.let { return blocked(it) }

        val open = executor.tap(WhatsAppSelectors.search, Expectation.ScreenChanged, WhatsAppSelectors.PACKAGE)
        if (open !is ActionResult.Success) return fromResult(open, "", "")
        val typed = typer.typeInto(WhatsAppSelectors.searchField, text, WhatsAppSelectors.PACKAGE)
        return if (typed is ActionResult.Success) null else fromResult(typed, "", "")
    }

    // ---- helpers -------------------------------------------------------------------------

    private suspend fun settle(): ScreenObservation? {
        for (attempt in 1..4) {
            val screen = executor.observe()
            if (screen != null && screen.packageName == WhatsAppSelectors.PACKAGE) return screen
            if (attempt < 4) pause(500)
        }
        return executor.observe()
    }

    /** Distinct chat names on screen, in order. */
    private fun contactRows(screen: ScreenObservation): List<String> =
        screen.elements
            .filter { it.viewId.contains(WhatsAppSelectors.CONTACT_ROW_ID) && it.text.isNotBlank() }
            .sortedBy { it.bounds.top }
            .map { it.text.trim() }
            .distinctBy { it.lowercase() }

    private fun notOnWhatsApp(screen: ScreenObservation): Boolean =
        WhatsAppSelectors.notOnWhatsApp.any { screen.containsText(it) }

    private fun fromResult(result: ActionResult, successCode: String, successMessage: String): WhatsAppOutcome = when (result) {
        is ActionResult.Success -> done(successCode, successMessage)
        is ActionResult.Blocked -> blocked(result.blocker)
        is ActionResult.Failed -> failed(result.reason.code, result.detail.ifBlank { result.reason.code.replace('_', ' ') })
    }

    private fun blocked(blocker: com.Lia.assistant.agent.core.Blocker) = WhatsAppOutcome(
        status = WhatsAppStatus.WAITING_FOR_USER,
        code = "waiting_for_user",
        message = "WhatsApp needs the user (${blocker.name.lowercase().replace('_', ' ')}). They must deal with it, then say resume.",
        blocker = blocker,
    )

    private fun done(code: String, message: String, items: List<String> = emptyList()) =
        WhatsAppOutcome(WhatsAppStatus.COMPLETED, code, message, items)

    private fun failed(code: String, message: String) = WhatsAppOutcome(WhatsAppStatus.FAILED, code, "Stopped: $message")
}
