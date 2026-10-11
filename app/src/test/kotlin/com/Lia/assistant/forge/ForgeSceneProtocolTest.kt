package com.Lia.assistant.forge

import com.Lia.assistant.ui.theme.NovaDarkColors
import com.Lia.assistant.ui.theme.NovaLightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeSceneProtocolTest {
    private fun state(
        buildId: Long = 1,
        phase: ForgePhase = ForgePhase.BUILDING,
        stage: ForgeStage = ForgeStage.PLANNING,
        progress: Float = 0.1f,
        chars: Int = 100,
        sections: List<String> = emptyList(),
        log: List<String> = emptyList(),
        logSeq: Long = log.size.toLong(),
    ) = ForgeUiState(
        phase = phase, stage = stage, progress = progress, metrics = ForgeMetrics(chars = chars),
        sections = sections, log = log, logSeq = logSeq, buildId = buildId,
    )

    @Test fun theFirstDiffSendsEverythingAndAReset() {
        val d = ForgeSceneProtocol.diff(null, state(log = listOf("Planning")))!!
        assertTrue(d.getBoolean("reset"))
        assertEquals("building", d.getString("phase"))
        assertEquals(0, d.getInt("stage"))
        assertEquals(0.1, d.getDouble("progress"), 0.0001)
        assertEquals(100, d.getInt("chars"))
        assertEquals("Planning", d.getJSONArray("log").getString(0))
        assertFalse(d.has("newSections"))
    }

    @Test fun nothingChangedMeansNothingIsSent() {
        val s = state(log = listOf("a"))
        assertNull(ForgeSceneProtocol.diff(s, s.copy()))
    }

    @Test fun tinyProgressStepsAreSkippedAndBiggerOnesAreRounded() {
        val a = state(progress = 0.100f)
        assertNull(ForgeSceneProtocol.diff(a, a.copy(progress = 0.1015f)))
        val d = ForgeSceneProtocol.diff(a, a.copy(progress = 0.20449f))!!
        assertEquals(0.204, d.getDouble("progress"), 0.00001)
        assertEquals(setOf("progress"), d.keys().asSequence().toSet())
    }

    @Test fun aNewStageIsSentAsItsNumber() {
        val a = state()
        val d = ForgeSceneProtocol.diff(a, a.copy(stage = ForgeStage.VISUALS))!!
        assertEquals(3, d.getInt("stage"))
        assertFalse(d.has("reset"))
    }

    @Test fun onlyNewSectionsAreSent() {
        val a = state(sections = listOf("Header", "Hero"))
        val b = a.copy(sections = listOf("Header", "Hero", "About", "Pricing"))
        val d = ForgeSceneProtocol.diff(a, b)!!
        val names = d.getJSONArray("newSections")
        assertEquals(2, names.length())
        assertEquals("About", names.getString(0))
        assertEquals("Pricing", names.getString(1))
    }

    @Test fun onlyNewLogLinesAreSent() {
        val a = state(log = listOf("a", "b", "c"), logSeq = 3)
        val b = a.copy(log = listOf("a", "b", "c", "d", "e"), logSeq = 5)
        val lines = ForgeSceneProtocol.diff(a, b)!!.getJSONArray("log")
        assertEquals(2, lines.length())
        assertEquals("d", lines.getString(0))
        assertEquals("e", lines.getString(1))
    }

    @Test fun whenTheLogWasTrimmedOnlyWhatIsStillThereIsSent() {
        val a = state(log = listOf("x"), logSeq = 1)
        val b = a.copy(log = listOf("y", "z"), logSeq = 50)
        val lines = ForgeSceneProtocol.diff(a, b)!!.getJSONArray("log")
        assertEquals(2, lines.length())
    }

    @Test fun aNewBuildStartsFromScratch() {
        val a = state(buildId = 1, sections = listOf("Header"), log = listOf("a"))
        val b = state(buildId = 2, sections = listOf("Header"), log = listOf("Planning"))
        val d = ForgeSceneProtocol.diff(a, b)!!
        assertTrue(d.getBoolean("reset"))
        assertEquals("Header", d.getJSONArray("newSections").getString(0))
        assertEquals("Planning", d.getJSONArray("log").getString(0))
    }

    @Test fun aPhaseChangeIsSent() {
        val a = state()
        assertEquals("failed", ForgeSceneProtocol.diff(a, a.copy(phase = ForgePhase.FAILED))!!.getString("phase"))
        assertEquals("done", ForgeSceneProtocol.diff(a, a.copy(phase = ForgePhase.DONE))!!.getString("phase"))
    }

    @Test fun lineSeparatorsAreEscapedForJavaScript() {
        val s = state(log = listOf("a\u2028b"))
        val js = ForgeSceneProtocol.toJs(ForgeSceneProtocol.diff(null, s)!!)
        assertFalse(js.contains("\u2028"))
        assertTrue(js.contains("\\u2028"))
    }
}

class ForgeThemeJsonTest {
    @Test fun colorsBecomeHex() {
        assertEquals("#FFB547", ForgeThemeJson.hex(androidx.compose.ui.graphics.Color(0xFFFFB547)))
        assertEquals("#06050E", ForgeThemeJson.hex(androidx.compose.ui.graphics.Color(0xFF06050E)))
        assertEquals("#000000", ForgeThemeJson.hex(androidx.compose.ui.graphics.Color.Black))
    }

    @Test fun theThemeCarriesTheTokensTheSceneUses() {
        val t = ForgeThemeJson.theme(NovaDarkColors)
        assertEquals("#06050E", t.getString("background"))
        assertEquals("#15113A", t.getString("backgroundTop"))
        assertEquals("#FFB547", t.getString("accent"))
        assertEquals("#3FE0D0", t.getString("lagoon"))
        assertEquals("#FF6FAE", t.getString("lotus"))
        assertTrue(t.getBoolean("isDark"))
        for (key in listOf("background", "backgroundTop", "accent", "accentSecondary", "lagoon", "lotus", "textPrimary", "textSecondary", "textTertiary", "error", "success")) {
            assertTrue(key, Regex("#[0-9A-F]{6}").matches(t.getString(key)))
        }
    }

    @Test fun theLightThemeIsMarkedLight() {
        val t = ForgeThemeJson.theme(NovaLightColors)
        assertFalse(t.getBoolean("isDark"))
        assertEquals("#F3F0FB", t.getString("background"))
    }

    @Test fun optionsCarryTheStageLabelsInOrder() {
        val o = ForgeThemeJson.options(lowRam = true, reducedMotion = false)
        assertTrue(o.getBoolean("lowRam"))
        assertFalse(o.getBoolean("reducedMotion"))
        val labels = o.getJSONArray("stageLabels")
        assertEquals(6, labels.length())
        assertEquals("Planning", labels.getString(0))
        assertEquals("Finishing", labels.getString(5))
        assertNotNull(o)
    }
}

class ForgeFilesTest {
    @Test fun theTitleIsTidied() {
        assertEquals("My Bakery", ForgeFiles.titleOf("<html><head><title>  My \n  Bakery </title></head>"))
        assertEquals("Shop", ForgeFiles.titleOf("<TITLE lang=\"en\">Shop</TITLE>"))
    }

    @Test fun noTitleOrAnEmptyOneGivesNull() {
        assertNull(ForgeFiles.titleOf("<html><body>hi</body></html>"))
        assertNull(ForgeFiles.titleOf("<title>   </title>"))
    }

    @Test fun aLongTitleIsCut() = assertEquals(60, ForgeFiles.titleOf("<title>${"a".repeat(200)}</title>")!!.length)
}
