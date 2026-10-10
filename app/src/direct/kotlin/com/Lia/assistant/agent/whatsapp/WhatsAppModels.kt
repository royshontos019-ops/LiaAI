package com.Lia.assistant.agent.whatsapp

import com.Lia.assistant.agent.core.Blocker

enum class WhatsAppAction(val code: String) {
    READ_UNREAD("read_unread"),
    SEND_MESSAGE("send_message"),
    READ_CHAT("read_chat"),
    SEARCH_CHAT("search_chat"),
    MUTE_CHAT("mute_chat"),
    UNMUTE_CHAT("unmute_chat"),
    MARK_READ("mark_read"),
    ;

    companion object {
        fun fromCode(code: String): WhatsAppAction? = entries.firstOrNull { it.code == code.trim().lowercase() }
    }
}

sealed interface WhatsAppParse {
    data class Valid(val request: WhatsAppRequest) : WhatsAppParse
    data class Invalid(val code: String, val message: String) : WhatsAppParse
}

data class WhatsAppRequest(
    val action: WhatsAppAction,
    /** Digits only, with country code. */
    val phone: String?,
    val contactName: String?,
    val message: String,
    val query: String,
    val limit: Int,
) {
    companion object {
        const val DEFAULT_LIMIT = 10
        const val MAX_LIMIT = 30
        const val MAX_MESSAGE = 4000

        fun parse(args: Map<String, String?>): WhatsAppParse {
            fun arg(name: String): String = args[name]?.trim().orEmpty()

            val actionText = arg("action")
            if (actionText.isEmpty()) return invalid("missing_action", "Which action: read_unread, send_message, read_chat, search_chat, mute_chat, unmute_chat or mark_read?")
            val action = WhatsAppAction.fromCode(actionText)
                ?: return invalid("unknown_action", "Unknown action \"$actionText\".")

            val phoneText = arg("phone_number")
            val phone: String? = if (phoneText.isEmpty()) null else when (val p = normalizePhone(phoneText)) {
                is PhoneResult.Ok -> p.digits
                is PhoneResult.Bad -> return invalid(p.code, p.message)
            }
            val name = arg("contact_name").ifEmpty { null }
            val message = arg("message")
            val query = arg("query")

            when (action) {
                WhatsAppAction.READ_UNREAD -> Unit
                WhatsAppAction.SEND_MESSAGE -> {
                    if (phone == null) {
                        return invalid("phone_number_required", "To send, give the phone number with country code. Ask the user for it.")
                    }
                    if (message.isEmpty()) return invalid("message_required", "What should the message say?")
                    if (message.length > MAX_MESSAGE) return invalid("message_too_long", "That message is too long.")
                }
                WhatsAppAction.SEARCH_CHAT ->
                    if (query.isEmpty()) return invalid("query_required", "What should I search for?")
                WhatsAppAction.READ_CHAT, WhatsAppAction.MUTE_CHAT, WhatsAppAction.UNMUTE_CHAT, WhatsAppAction.MARK_READ ->
                    if (phone == null && name == null) {
                        return invalid("chat_required", "Which chat? Give a contact name or a phone number.")
                    }
            }

            val limit = arg("limit").toIntOrNull()?.coerceIn(1, MAX_LIMIT) ?: DEFAULT_LIMIT
            return WhatsAppParse.Valid(WhatsAppRequest(action, phone, name, message, query, limit))
        }

        private fun invalid(code: String, message: String) = WhatsAppParse.Invalid(code, message)

        sealed interface PhoneResult {
            data class Ok(val digits: String) : PhoneResult
            data class Bad(val code: String, val message: String) : PhoneResult
        }

        /** Digits with country code, or a reason it cannot be used. A local number like 017... is refused, not guessed. */
        fun normalizePhone(raw: String): PhoneResult {
            val trimmed = raw.trim()
            var digits = trimmed.filter { it.isDigit() }
            if (digits.startsWith("00")) digits = digits.drop(2)
            else if (!trimmed.startsWith("+") && digits.startsWith("0")) {
                return PhoneResult.Bad("phone_needs_country_code", "Give the number with its country code, for example +8801712345678.")
            }
            if (digits.length !in 8..15) return PhoneResult.Bad("phone_invalid", "That does not look like a phone number.")
            return PhoneResult.Ok(digits)
        }
    }
}

enum class WhatsAppStatus {
    RUNNING,
    WAITING_FOR_USER,
    COMPLETED,
    FAILED,
    CANCELLED,
    ;

    val isFinished: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED
}

/** What one WhatsApp action ended with. [items] holds messages, chats or unread previews. */
data class WhatsAppOutcome(
    val status: WhatsAppStatus,
    val code: String,
    val message: String,
    val items: List<String> = emptyList(),
    val blocker: Blocker? = null,
)

data class UnreadChat(val sender: String, val preview: String, val count: Int)

/** Where unread messages come from. Production: a notification listener. It never opens WhatsApp. */
interface UnreadMessageSource {
    val isAvailable: Boolean
    fun unread(): List<UnreadChat>
}

interface WhatsAppLauncher {
    /** Opens the chat with [phone] straight away. [prefill] is put in the message box, not sent. */
    suspend fun openChat(phone: String, prefill: String?): Boolean
    suspend fun openApp(): Boolean
}
