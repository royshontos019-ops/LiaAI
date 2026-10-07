package com.Lia.assistant.voice

/** One typed-chat message sent to Gemini as history. */
data class ChatTurn(val isUser: Boolean, val text: String)

sealed interface ChatReplyResult {
    data class Success(val text: String) : ChatReplyResult
    data class Error(val message: String) : ChatReplyResult
}
