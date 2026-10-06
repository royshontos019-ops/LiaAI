package com.Lia.assistant.data

enum class LanguagePreference(val displayName: String, val description: String) {
    AUTO("Auto", "Follows whatever you speak"),
    HINDI("Hindi", "Always replies in Hindi"),
    HINGLISH("Hinglish", "Hindi and English mixed, written in Roman letters"),
    ENGLISH("English", "Always replies in English");

    companion object {
        val DEFAULT = AUTO

        fun fromId(id: String?): LanguagePreference =
            entries.firstOrNull { it.name.equals(id?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}
