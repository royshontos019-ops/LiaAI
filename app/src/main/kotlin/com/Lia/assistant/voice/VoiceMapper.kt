package com.Lia.assistant.voice

/**
 * Maps whatever voice name the UI stores to a Gemini prebuilt voice.
 * Unknown names fall back to [DEFAULT] so the server never sees an invalid voice.
 * When the Settings screen defines nicknames, add them to [ALIASES].
 */
object VoiceMapper {
    const val DEFAULT = "Aoede"

    private val GEMINI_VOICES = listOf("Zephyr", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Aoede")

    /** lowercase UI name -> Gemini voice. */
    private val ALIASES: Map<String, String> = emptyMap()

    fun toGemini(uiName: String?): String {
        val key = uiName?.trim()?.lowercase().orEmpty()
        if (key.isEmpty()) return DEFAULT
        ALIASES[key]?.let { return it }
        return GEMINI_VOICES.firstOrNull { it.lowercase() == key } ?: DEFAULT
    }
}
