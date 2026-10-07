package com.Lia.assistant.ui.screens.chat

enum class MessageKind { NORMAL, FORGE, ERROR }

data class ChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val kind: MessageKind = MessageKind.NORMAL,
    /** True while a new assistant reply should still be revealed word by word. */
    val animate: Boolean = false,
)

/** Word-by-word reveal: tokens keep their trailing whitespace so joining them restores the text. */
object RevealText {
    const val MS_PER_WORD = 20L
    private val WORD = Regex("\\S+\\s*")

    fun tokens(text: String): List<String> = WORD.findAll(text).map { it.value }.toList()

    fun joined(tokens: List<String>, count: Int): String =
        tokens.take(count.coerceIn(0, tokens.size)).joinToString("")
}

/** Cleans markdown-ish characters so the speech engine does not read them out. */
object SpeechText {
    private val SYMBOLS = Regex("[*_`#>~]+")
    private val SPACES = Regex("\\s+")

    fun clean(text: String): String = text.replace(SYMBOLS, "").replace(SPACES, " ").trim()
}
