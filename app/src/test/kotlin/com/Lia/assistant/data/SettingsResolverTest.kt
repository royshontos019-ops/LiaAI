package com.Lia.assistant.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsResolverTest {
    @Test fun personality_storedWins() =
        assertEquals(Personality.CALM, SettingsResolver.personality("CALM", "PLAYFUL"))

    @Test fun personality_fallsBackToLegacy() =
        assertEquals(Personality.PLAYFUL, SettingsResolver.personality(null, "PLAYFUL"))

    @Test fun personality_blankStoredFallsBack() =
        assertEquals(Personality.PLAYFUL, SettingsResolver.personality("  ", "PLAYFUL"))

    @Test fun personality_unknownOrMissingGivesDefault() {
        assertEquals(Personality.DEFAULT, SettingsResolver.personality("nonsense", null))
        assertEquals(Personality.DEFAULT, SettingsResolver.personality(null, null))
    }

    @Test fun personality_caseInsensitive() =
        assertEquals(Personality.CALM, Personality.fromId("calm"))

    @Test fun language_defaultsToAuto() {
        assertEquals(LanguagePreference.AUTO, SettingsResolver.language(null, null))
        assertEquals(LanguagePreference.AUTO, SettingsResolver.language("xx", "yy"))
    }

    @Test fun language_storedThenLegacy() {
        assertEquals(LanguagePreference.HINGLISH, SettingsResolver.language("HINGLISH", "ENGLISH"))
        assertEquals(LanguagePreference.ENGLISH, SettingsResolver.language(null, "ENGLISH"))
    }

    @Test fun text_orderIsStoredThenLegacyThenDefault() {
        assertEquals("A", SettingsResolver.text("A", "B", "C"))
        assertEquals("B", SettingsResolver.text(null, "B", "C"))
        assertEquals("B", SettingsResolver.text("", "B", "C"))
        assertEquals("C", SettingsResolver.text(null, " ", "C"))
    }

    @Test fun themeMode_defaultsToDark() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromId(null))
        assertEquals(ThemeMode.DARK, ThemeMode.fromId("???"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromId("light"))
    }

    @Test fun brandConstants() {
        assertEquals("Lia", AssistantBrand.NAME)
        assertEquals("Lia AI", AssistantBrand.FULL_NAME)
        assertEquals("Your Intelligent AI Companion", AssistantBrand.TAGLINE)
    }
}
