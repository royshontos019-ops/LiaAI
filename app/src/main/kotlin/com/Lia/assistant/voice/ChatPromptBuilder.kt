package com.Lia.assistant.voice

import com.Lia.assistant.data.AssistantBrand
import com.Lia.assistant.data.LanguagePreference
import com.Lia.assistant.data.Personality

/** System prompt for typed chat. No tool instructions: chat cannot act on the phone. */
object ChatPromptBuilder {
    const val TYPED_CHAT_LINE =
        "This is typed chat, not a voice call. Never mention tapping, listening or speaking. " +
            "Short lists or code blocks are fine when they help."

    fun build(personality: Personality, language: LanguagePreference, assistantName: String): String {
        val name = assistantName.trim().ifEmpty { AssistantBrand.NAME }
        return listOf(
            "You are $name, a personal assistant living on the user's phone. " +
                "Keep replies short, clear and conversational.",
            TYPED_CHAT_LINE,
            personality.systemInstruction,
            languagePolicy(language),
            PersonalityPromptBuilder.SAFETY,
        ).joinToString("\n\n")
    }

    internal fun languagePolicy(language: LanguagePreference): String = when (language) {
        LanguagePreference.AUTO ->
            "Language: reply in the language the user writes in, message by message, and never " +
                "translate what they wrote. If they write Hinglish (Hindi in Roman letters), reply in Hinglish."
        LanguagePreference.HINDI -> "Language: always reply in Hindi, whatever language the user writes in."
        LanguagePreference.HINGLISH ->
            "Language: always reply in Hinglish (Hindi mixed with English, written in Roman letters), " +
                "whatever language the user writes in."
        LanguagePreference.ENGLISH -> "Language: always reply in English, whatever language the user writes in."
    }
}
