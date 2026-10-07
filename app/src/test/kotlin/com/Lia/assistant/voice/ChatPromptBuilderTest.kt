package com.Lia.assistant.voice

import com.Lia.assistant.LiaCapabilityPrompts
import com.Lia.assistant.data.LanguagePreference
import com.Lia.assistant.data.Personality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPromptBuilderTest {
    @Test fun everyPersonalityTimesLanguageBuilds() {
        for (p in Personality.entries) for (l in LanguagePreference.entries) {
            val out = ChatPromptBuilder.build(p, l, "Lia")
            assertTrue("$p/$l", out.contains(p.systemInstruction))
            assertTrue(out.contains("You are Lia"))
            assertTrue(out.contains(ChatPromptBuilder.TYPED_CHAT_LINE))
            assertTrue(out.contains(PersonalityPromptBuilder.SAFETY))
        }
    }

    @Test fun typedChatLineForbidsVoiceTalk() {
        val line = ChatPromptBuilder.TYPED_CHAT_LINE
        assertTrue(line.contains("typed chat"))
        assertTrue(line.contains("tapping"))
        assertTrue(line.contains("listening"))
        assertTrue(line.contains("speaking"))
    }

    @Test fun noToolInstructionsInChat() {
        val out = ChatPromptBuilder.build(Personality.NORMAL, LanguagePreference.AUTO, "Lia")
        assertFalse(out.contains(LiaCapabilityPrompts.toolInstruction("Lia")))
        assertFalse(out.contains("open_app"))
        assertFalse(out.contains("message_contact"))
    }

    @Test fun sectionsComeInTheRequestedOrder() {
        val out = ChatPromptBuilder.build(Personality.FUNNY, LanguagePreference.ENGLISH, "Lia")
        val base = out.indexOf("You are Lia")
        val typed = out.indexOf(ChatPromptBuilder.TYPED_CHAT_LINE)
        val personality = out.indexOf(Personality.FUNNY.systemInstruction)
        val language = out.indexOf("Language:")
        val safety = out.indexOf(PersonalityPromptBuilder.SAFETY)
        assertTrue(base in 0 until typed)
        assertTrue(typed < personality)
        assertTrue(personality < language)
        assertTrue(language < safety)
    }

    @Test fun languagePolicies() {
        assertTrue(ChatPromptBuilder.languagePolicy(LanguagePreference.AUTO).contains("writes in"))
        assertTrue(ChatPromptBuilder.languagePolicy(LanguagePreference.HINDI).contains("always reply in Hindi"))
        assertTrue(ChatPromptBuilder.languagePolicy(LanguagePreference.HINGLISH).contains("always reply in Hinglish"))
        assertTrue(ChatPromptBuilder.languagePolicy(LanguagePreference.ENGLISH).contains("always reply in English"))
    }

    @Test fun blankNameFallsBackToLia() =
        assertTrue(ChatPromptBuilder.build(Personality.NORMAL, LanguagePreference.AUTO, "  ").contains("You are Lia"))

    @Test fun nameIsUsed() =
        assertTrue(ChatPromptBuilder.build(Personality.NORMAL, LanguagePreference.AUTO, "Zara").contains("You are Zara"))

    @Test fun developerPersonalityNoLongerClaimsRepliesAreSpoken() {
        assertFalse(Personality.DEVELOPER.systemInstruction.contains("Replies are spoken"))
        assertEquals(true, Personality.DEVELOPER.systemInstruction.startsWith("Personality: Developer."))
    }
}
