package com.Lia.assistant.voice

/** What the user asked the Website Forge to build. [topic] is the part after "for/about/called", if any. */
data class ForgeRequest(val prompt: String, val topic: String?)

/**
 * Decides whether a typed message is a request to BUILD a website (so the Forge should open)
 * rather than a question about websites. Pure Kotlin.
 */
object ForgeIntent {
    private const val MAX_LENGTH = 800
    private const val MAX_WORDS_BETWEEN_VERB_AND_NOUN = 8

    private val SPLIT = Regex("[^\\p{L}\\p{M}\\p{N}']+")
    private val TOPIC = Regex("\\b(?:for|about|called|named)\\b\\s+(.+)", RegexOption.IGNORE_CASE)

    private val QUESTION_STARTERS = setOf(
        "how", "what", "whats", "what's", "why", "when", "where", "which", "who",
        "is", "are", "does", "do", "did", "should", "will",
    )
    private val LEARNING_CUES = listOf("how to", "how do", "how can", "learn", "tutorial", "explain", "what is", "difference between")

    /** A preposition between verb and noun means the website is not the thing being built ("write a post for my website"). */
    private val PREPOSITIONS = setOf("for", "on", "in", "at", "about", "to", "of", "from", "with", "into", "onto", "under", "over")

    private val EN_VERBS = setOf("build", "make", "create", "design", "generate", "develop", "code", "write", "craft")

    /** Hinglish, Hindi and Bangla verbs; their word order is free ("ek website banao"). */
    private val OTHER_VERBS = setOf(
        "banao", "bana", "banado", "banana", "bnao",
        "बनाओ", "बना", "बनाइए",
        "বানাও", "বানিয়ে", "তৈরি", "বানান",
    )

    private val NOUNS = setOf("website", "webpage", "homepage", "landingpage", "ওয়েবসাইট", "वेबसाइट")

    private val JOINED = listOf(
        "web site" to "website", "web page" to "webpage", "home page" to "homepage",
        "landing page" to "landingpage", "ওয়েব সাইট" to "ওয়েবসাইট",
    )

    fun extract(text: String): ForgeRequest? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) return null

        var normalized = trimmed.lowercase()
        for ((from, to) in JOINED) normalized = normalized.replace(from, to)
        if (LEARNING_CUES.any { it in normalized }) return null

        val words = normalized.split(SPLIT).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.first() in QUESTION_STARTERS) return null

        val nounAt = words.indexOfFirst { it in NOUNS }
        if (nounAt < 0) return null

        val englishOrder = (0 until nounAt).any { i ->
            words[i] in EN_VERBS &&
                nounAt - i <= MAX_WORDS_BETWEEN_VERB_AND_NOUN &&
                words.subList(i + 1, nounAt).none { it in PREPOSITIONS }
        }
        val otherVerb = words.any { it in OTHER_VERBS }
        if (!englishOrder && !otherVerb) return null

        val topic = TOPIC.find(trimmed)?.groupValues?.get(1)?.trim()?.trimEnd('.', '!', '?', ' ')?.takeIf { it.isNotEmpty() }
        return ForgeRequest(trimmed, topic)
    }
}
