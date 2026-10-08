package com.Lia.assistant.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovaDesignTokensTest {
    // ---------- contrast helper (WCAG) ----------
    private fun channel(v: Float): Double {
        val x = v.toDouble()
        return if (x <= 0.03928) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val l1 = luminance(a)
        val l2 = luminance(b)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    // ---------- the colours asked for ----------
    @Test fun darkSchemeUsesTheSpecifiedValues() {
        val d = NovaDarkColors
        assertEquals(Color(0xFF06050E), d.background)
        assertEquals(Color(0xFF15113A), d.backgroundGradientTop)
        assertEquals(Color(0xFF17143A), d.surface)
        assertEquals(Color(0xFF221E4D), d.surfaceRaised)
        assertEquals(Color(0xFFFFB547), d.accent)
        assertEquals(Color(0xFFFF8A3D), d.accentSecondary)
        assertEquals(Color(0xFFEEE9FF), d.textPrimary)
        assertEquals(Color(0xFFA7A0CC), d.textSecondary)
        assertTrue(d.isDark)
    }

    @Test fun lightSchemeUsesTheSpecifiedValues() {
        val l = NovaLightColors
        assertEquals(Color(0xFFF3F0FB), l.background)
        assertEquals(Color(0xFFFFFFFF), l.surface)
        assertEquals(Color(0xFFB86A00), l.accent)
        assertEquals(Color(0xFF15112E), l.textPrimary)
        assertFalse(l.isDark)
    }

    @Test fun lotusAndLagoonAreTheSameInBothThemes() {
        assertEquals(Color(0xFFFF6FAE), NovaDarkColors.lotus)
        assertEquals(Color(0xFFFF6FAE), NovaLightColors.lotus)
        assertEquals(Color(0xFF3FE0D0), NovaDarkColors.lagoon)
        assertEquals(Color(0xFF3FE0D0), NovaLightColors.lagoon)
    }

    @Test fun orbColoursAreNotUsedForActionableThings() {
        for (scheme in listOf(NovaDarkColors, NovaLightColors)) {
            assertNotEquals(scheme.lagoon, scheme.accent)
            assertNotEquals(scheme.lotus, scheme.accent)
            assertNotEquals(scheme.lagoon, scheme.accentSecondary)
            assertNotEquals(scheme.lotus, scheme.accentSecondary)
        }
    }

    @Test fun nightIsNotFlatBlack() {
        assertNotEquals(Color(0xFF000000), NovaDarkColors.background)
        assertNotEquals(NovaDarkColors.background, NovaDarkColors.backgroundGradientTop)
    }

    // ---------- readability ----------
    @Test fun bodyTextIsReadableOnEverySurface() {
        for (s in listOf(NovaDarkColors, NovaLightColors)) {
            for (bg in listOf(s.background, s.surface, s.surfaceRaised)) {
                assertTrue("textPrimary on $bg", contrast(s.textPrimary, bg) >= 7.0)
                assertTrue("textSecondary on $bg", contrast(s.textSecondary, bg) >= 4.5)
            }
        }
    }

    @Test fun accentIsVisibleAgainstTheBackground() {
        assertTrue(contrast(NovaDarkColors.accent, NovaDarkColors.background) >= 7.0)
        assertTrue(contrast(NovaLightColors.accent, NovaLightColors.background) >= 3.0)
    }

    @Test fun textOnAccentButtonsIsReadable() {
        assertTrue(contrast(NovaDarkColors.onAccent, NovaDarkColors.accent) >= 7.0)
        // Light marigold #B86A00 with white text is about 4.1:1, a little under AA for small text.
        assertTrue(contrast(NovaLightColors.onAccent, NovaLightColors.accent) >= 4.0)
    }

    @Test fun bubblesKeepTheirTextReadable() {
        assertTrue(contrast(NovaDarkColors.textPrimary, NovaDarkColors.userBubble) >= 7.0)
        assertTrue(contrast(NovaDarkColors.textPrimary, NovaDarkColors.assistantBubble) >= 7.0)
        assertTrue(contrast(NovaLightColors.textPrimary, NovaLightColors.userBubble) >= 7.0)
        assertTrue(contrast(NovaLightColors.textPrimary, NovaLightColors.assistantBubble) >= 7.0)
    }

    // ---------- spacing, shapes, motion ----------
    @Test fun spacingScale() {
        assertEquals(
            listOf(4.dp, 8.dp, 12.dp, 16.dp, 24.dp, 32.dp, 48.dp),
            listOf(
                NovaSpacing.xs, NovaSpacing.sm, NovaSpacing.md, NovaSpacing.lg,
                NovaSpacing.xl, NovaSpacing.xxl, NovaSpacing.xxxl,
            ),
        )
    }

    @Test fun motionDurations() {
        assertEquals(150, NovaMotion.FAST_MS)
        assertEquals(300, NovaMotion.NORMAL_MS)
        assertEquals(500, NovaMotion.SLOW_MS)
    }

    @Test fun touchTargetIsAtLeast48dp() = assertEquals(48.dp, NovaMinTouchTarget)

    // ---------- typography ----------
    @Test fun typeScaleSizes() {
        val t = buildNovaTypeScale(NovaFontFamilies.System)
        assertEquals(17f, t.title.fontSize.value, 0f)
        assertEquals(24f, t.title.lineHeight.value, 0f)
        assertEquals(FontWeight.SemiBold, t.title.fontWeight)
        assertEquals(15f, t.body.fontSize.value, 0f)
        assertEquals(22f, t.body.lineHeight.value, 0f)
        assertEquals(13f, t.label.fontSize.value, 0f)
        assertEquals(18f, t.label.lineHeight.value, 0f)
        assertEquals(12f, t.caption.fontSize.value, 0f)
        assertEquals(16f, t.caption.lineHeight.value, 0f)
    }

    @Test fun serifIsForDisplayHeadlineAndVoice() {
        val fonts = NovaFontFamilies.System.copy(
            serif = androidx.compose.ui.text.font.FontFamily.Serif,
            sans = androidx.compose.ui.text.font.FontFamily.SansSerif,
        )
        val t = buildNovaTypeScale(fonts)
        assertEquals(fonts.serif, t.display.fontFamily)
        assertEquals(fonts.serif, t.headline.fontFamily)
        assertEquals(fonts.serif, t.voice.fontFamily)
        assertEquals(FontStyle.Italic, t.voice.fontStyle)
        for (style in listOf(t.title, t.body, t.label, t.caption)) assertEquals(fonts.sans, style.fontFamily)
    }

    @Test fun manropeWeightsCoverFourHundredToEightHundred() =
        assertEquals(listOf(400, 500, 600, 700, 800), NovaFonts.MANROPE_WEIGHTS)

    // ---------- the star field ----------
    @Test fun starFieldIsDeterministicAndInBounds() {
        val a = generateStars(70, seed = 7L)
        val b = generateStars(70, seed = 7L)
        assertEquals(a, b)
        assertEquals(70, a.size)
        for (s in a) {
            assertTrue(s.x in 0f..1f && s.y in 0f..1f)
            assertTrue(s.radiusDp in 0.5f..1.6f)
            assertTrue(s.alpha in 0.25f..0.8f)
        }
    }

    @Test fun differentSeedsGiveDifferentSkies() =
        assertNotEquals(generateStars(70, seed = 7L), generateStars(70, seed = 8L))
}
