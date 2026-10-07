package com.Lia.assistant.voice

import com.Lia.assistant.LiaCapabilityPrompts
import com.Lia.assistant.data.AssistantBrand
import com.Lia.assistant.data.LanguagePreference
import com.Lia.assistant.data.Personality

object PersonalityPromptBuilder {
    const val RECAP_MARKER = "This is a continuing conversation"

    internal const val SAFETY =
        "Safety rules: Never claim to be human or to have a body. Never produce sexually explicit " +
            "content. If the user asks you to stop a topic or change the tone, do it immediately. " +
            "Vary your phrasing so you do not repeat the same sentences."

    fun build(
        personality: Personality,
        language: LanguagePreference,
        detectedLanguage: DetectedLanguage,
        recap: String?,
        assistantName: String,
    ): String {
        val name = assistantName.trim().ifEmpty { AssistantBrand.NAME }
        val sections = buildList {
            add(
                "You are $name, a persistent personal voice assistant living on the user's phone. " +
                    "Keep replies short and conversational, like spoken conversation.",
            )
            add(LiaCapabilityPrompts.toolInstruction(name))
            add(personality.systemInstruction)
            add(languagePolicy(language, detectedLanguage))
            add(SAFETY)
            recap?.takeIf { it.isNotBlank() }?.let {
                add("$RECAP_MARKER — do not greet again. Recap:\n$it")
            }
        }
        return sections.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")
    }

    private fun languagePolicy(language: LanguagePreference, detected: DetectedLanguage): String =
        when (language) {
            LanguagePreference.AUTO -> {
                val base = "Language: match the language the user speaks, turn by turn, and never " +
                    "translate what they said."
                val hint = when (detected) {
                    DetectedLanguage.HINDI -> " Their latest turn was in Hindi, so reply in Hindi."
                    DetectedLanguage.HINGLISH ->
                        " Their latest turn was in Hinglish, so reply in Hinglish (Hindi mixed with English, in Roman letters)."
                    DetectedLanguage.ENGLISH -> " Their latest turn was in English, so reply in English."
                    DetectedLanguage.UNKNOWN -> ""
                }
                base + hint
            }
            LanguagePreference.HINDI -> "Language: always reply in Hindi, whatever language the user speaks."
            LanguagePreference.HINGLISH ->
                "Language: always reply in Hinglish (Hindi mixed with English, written in Roman letters), whatever language the user speaks."
            LanguagePreference.ENGLISH -> "Language: always reply in English, whatever language the user speaks."
        }
}
