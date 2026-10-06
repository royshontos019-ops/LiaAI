package com.Lia.assistant.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLauncherTest {
    private val apps = mapOf(
        "whatsapp" to "com.whatsapp",
        "whatsapp business" to "com.whatsapp.w4b",
        "youtube" to "com.yt",
        "youtube music" to "com.ytm",
        "chrome" to "com.chrome",
        "calculator" to "com.calc",
        "tv" to "com.tv",
        "google maps" to "com.maps",
    )

    private fun pkg(query: String) = AppLauncher.findApp(apps, query)?.packageName

    @Test fun exactMatchWins() = assertEquals("com.whatsapp", pkg("WhatsApp"))

    @Test fun exactMatchIsCaseAndSpaceInsensitive() = assertEquals("com.ytm", pkg("  YouTube   Music "))

    @Test fun exactBeatsLongerPrefixCandidates() = assertEquals("com.yt", pkg("youtube"))

    @Test fun labelStartingWithQueryPicksTheClosestLength() = assertEquals("com.whatsapp", pkg("whats"))

    @Test fun queryStartingWithLabel() = assertEquals("com.chrome", pkg("chrome browser"))

    @Test fun everyQueryWordPrefixesSomeLabelWord() {
        assertEquals("com.ytm", pkg("music"))
        assertEquals("com.ytm", pkg("you music"))
        assertEquals("com.maps", pkg("maps"))
        assertEquals("com.whatsapp.w4b", pkg("business"))   // a whole word of the label, so it counts
    }

    @Test fun neverLooseSubstringMatching() {
        assertNull(pkg("tube"))
        assertNull(pkg("app"))
        assertNull(pkg("culator"))
    }

    @Test fun shortKeysAreNotCandidatesUnlessExact() {
        assertEquals("com.tv", pkg("tv"))
        assertNull(pkg("tv guide"))
    }

    @Test fun oneCharacterOrBlankQueriesMatchNothing() {
        assertNull(pkg("w"))
        assertNull(pkg(""))
        assertNull(pkg("   "))
    }

    @Test fun closestLengthDecidesBetweenCandidates() {
        val m = mapOf("maps" to "a", "mapsme" to "b")
        assertEquals("a", AppLauncher.findApp(m, "map")?.packageName)
    }

    @Test fun unknownAppIsNull() = assertNull(pkg("spotify"))
}
