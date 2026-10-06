package com.Lia.assistant.voice

import com.Lia.assistant.LiaCapabilityPrompts
import com.Lia.assistant.data.LanguagePreference
import com.Lia.assistant.data.Personality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalityPromptBuilderTest {
    private fun build(
        p: Personality = Personality.NORMAL,
        l: LanguagePreference = LanguagePreference.AUTO,
        d: DetectedLanguage = DetectedLanguage.UNKNOWN,
        recap: String? = null,
        name: String = "Lia",
    ) = PersonalityPromptBuilder.build(p, l, d, recap, name)

    @Test fun everyPersonalityTimesLanguageBuilds() {
        for (p in Personality.entries) for (l in LanguagePreference.entries) for (d in DetectedLanguage.entries) {
            val out = build(p, l, d)
            assertTrue("$p/$l/$d", out.contains(p.systemInstruction))
            assertTrue(out.contains("You are Lia"))
            assertTrue(out.contains("Never claim to be human"))
            assertFalse(out.contains("\n\n\n"))
        }
    }

    @Test fun defaultIsNormal() = assertEquals(Personality.NORMAL, Personality.DEFAULT)

    @Test fun everyInstructionStartsWithPersonalityName() {
        for (p in Personality.entries)
            assertTrue(p.name, p.systemInstruction.startsWith("Personality: ${p.displayName}."))
    }

    @Test fun companionAndGfModeCarryTheSafetyClauses() {
        for (p in listOf(Personality.COMPANION, Personality.GF_MODE)) {
            val s = p.systemInstruction.lowercase()
            assertTrue(p.name, s.contains("never claim to be human"))
            assertTrue(p.name, s.contains("body"))
            assertTrue(p.name, s.contains("sexually explicit"))
            assertTrue(p.name, s.contains("change tone or topic"))
            assertTrue(p.name, s.contains("immediately"))
        }
    }

    @Test fun recapSectionOnlyWithRecap() {
        assertFalse(build(recap = null).contains(PersonalityPromptBuilder.RECAP_MARKER))
        val with = build(recap = "User: hi\nYou: hello")
        assertTrue(with.contains(PersonalityPromptBuilder.RECAP_MARKER))
        assertTrue(with.contains("do not greet again"))
        assertTrue(with.endsWith("User: hi\nYou: hello"))
    }

    @Test fun autoLanguageMatchesTurnByTurn() {
        val out = build(l = LanguagePreference.AUTO, d = DetectedLanguage.HINGLISH)
        assertTrue(out.contains("turn by turn"))
        assertTrue(out.contains("never translate"))
        assertTrue(out.contains("Hinglish"))
    }

    @Test fun fixedLanguageIsExplicit() {
        assertTrue(build(l = LanguagePreference.ENGLISH).contains("always reply in English"))
        assertTrue(build(l = LanguagePreference.HINDI).contains("always reply in Hindi"))
        assertTrue(build(l = LanguagePreference.HINGLISH).contains("always reply in Hinglish"))
    }

    @Test fun assistantNameIsUsedAndBlankFallsBack() {
        assertTrue(build(name = "Zara").contains("You are Zara"))
        assertTrue(build(name = "  ").contains("You are Lia"))
    }

    @Test fun flavorToolInstructionIsIncluded() {
        val tool = LiaCapabilityPrompts.toolInstruction("Lia")
        if (tool.isNotBlank()) assertTrue(build().contains(tool))
    }
}
