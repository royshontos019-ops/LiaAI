package com.Lia.assistant.voice

enum class DetectedLanguage { HINDI, HINGLISH, ENGLISH, UNKNOWN }

/** Cheap on-device guess at the language of one user turn. Pure Kotlin, JVM-testable. */
object LanguageDetector {
    private val SPLITTER = Regex("[^a-zA-Z']+")

    private val HINGLISH_MARKERS = setOf(
        "hai", "hain", "ho", "kya", "kyun", "kaise", "kaisa", "kaisi", "nahi", "nahin", "nhi",
        "haan", "mujhe", "tumhe", "tum", "aap", "mera", "meri", "tera", "teri", "yaar", "bhai",
        "accha", "acha", "theek", "thik", "matlab", "karna", "karo", "kar", "raha", "rahi",
        "chahiye", "batao", "bolo", "suno", "dekho", "abhi", "phir", "lekin", "bahut", "thoda",
        "kuch", "sab", "wala", "wali", "mein", "aaj", "kal", "toh", "bhi", "hoga", "tha", "thi",
        "samajh", "chalo", "arre", "zyada", "jaldi", "kyunki",
    )

    fun detect(text: String): DetectedLanguage {
        if (text.isBlank()) return DetectedLanguage.UNKNOWN
        if (text.any { it in '\u0900'..'\u097F' }) return DetectedLanguage.HINDI

        val words = text.lowercase().split(SPLITTER).filter { it.isNotEmpty() }
        if (words.isEmpty()) return DetectedLanguage.UNKNOWN // digits/punctuation only

        val hits = words.count { it in HINGLISH_MARKERS }
        val hinglish = if (words.size <= 4) {
            hits >= 1
        } else {
            hits >= 2 || hits.toDouble() / words.size >= 0.15
        }
        return if (hinglish) DetectedLanguage.HINGLISH else DetectedLanguage.ENGLISH
    }
}
