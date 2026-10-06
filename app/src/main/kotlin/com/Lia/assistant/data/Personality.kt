package com.Lia.assistant.data

/**
 * PLACEHOLDER set. The final list, icons and system instructions come from "Part 03";
 * replace the entries below when that spec arrives. Keep the enum constant names stable
 * because they are what gets saved to disk.
 */
enum class Personality(
    val displayName: String,
    val description: String,
    val icon: String,
    val systemInstruction: String,
) {
    FRIENDLY(
        "Friendly", "Warm, casual and encouraging", "😊",
        "You are a warm, friendly companion. Keep replies short, natural and encouraging.",
    ),
    PROFESSIONAL(
        "Professional", "Clear, precise and to the point", "💼",
        "You are a precise, professional assistant. Be concise, structured and accurate.",
    ),
    PLAYFUL(
        "Playful", "Light-hearted with a bit of humour", "😄",
        "You are a playful assistant with a light sense of humour. Stay helpful and never mean.",
    ),
    CALM(
        "Calm", "Gentle, patient and reassuring", "🌿",
        "You are a calm, patient assistant. Speak gently and explain things step by step.",
    );

    companion object {
        val DEFAULT = FRIENDLY

        fun fromId(id: String?): Personality =
            entries.firstOrNull { it.name.equals(id?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}
