package com.Lia.assistant.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Every colour a screen may use. Screens read these through NovaTheme.colors; they never write
 * a hex value. Marigold ([accent]) marks everything actionable. [lagoon] and [lotus] belong to
 * the orb (and the voice visualizer) only.
 */
data class NovaColorScheme(
    val background: Color,
    val backgroundGradientTop: Color,
    val surface: Color,
    val surfaceGlass: Color,
    val surfaceBorder: Color,
    val surfaceRaised: Color,
    val accent: Color,
    val accentSecondary: Color,
    val accentGlow: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val userBubble: Color,
    val assistantBubble: Color,
    val depthShadow: Color,
    val rimLight: Color,
    val lagoon: Color,
    val lotus: Color,
    val isDark: Boolean,
)

/** A pearl of light in the night: deep indigo, never flat black. */
val NovaDarkColors = NovaColorScheme(
    background = Color(0xFF06050E),
    backgroundGradientTop = Color(0xFF15113A),
    surface = Color(0xFF17143A),
    surfaceGlass = Color(0xB817143A),
    surfaceBorder = Color(0x26FFFFFF),
    surfaceRaised = Color(0xFF221E4D),
    accent = Color(0xFFFFB547),
    accentSecondary = Color(0xFFFF8A3D),
    accentGlow = Color(0x66FFB547),
    onAccent = Color(0xFF241400),
    textPrimary = Color(0xFFEEE9FF),
    textSecondary = Color(0xFFA7A0CC),
    textTertiary = Color(0xFF7D77A8),
    success = Color(0xFF4ADE9B),
    warning = Color(0xFFF2C14E),
    error = Color(0xFFFF6B7D),
    userBubble = Color(0xFF332A73),
    assistantBubble = Color(0xFF1D1A47),
    depthShadow = Color(0xB30B0833),
    rimLight = Color(0x40FFFFFF),
    lagoon = Color(0xFF3FE0D0),
    lotus = Color(0xFFFF6FAE),
    isDark = true,
)

val NovaLightColors = NovaColorScheme(
    background = Color(0xFFF3F0FB),
    backgroundGradientTop = Color(0xFFE6E0F8),
    surface = Color(0xFFFFFFFF),
    surfaceGlass = Color(0xCCFFFFFF),
    surfaceBorder = Color(0x2615112E),
    surfaceRaised = Color(0xFFECE7FA),
    accent = Color(0xFFB86A00),
    accentSecondary = Color(0xFFC2410C),
    accentGlow = Color(0x40B86A00),
    onAccent = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF15112E),
    textSecondary = Color(0xFF575078),
    textTertiary = Color(0xFF7A7499),
    success = Color(0xFF0F7B55),
    warning = Color(0xFF8F5E00),
    error = Color(0xFFB3261E),
    userBubble = Color(0xFFFFE3BD),
    assistantBubble = Color(0xFFFFFFFF),
    depthShadow = Color(0x662A1F66),
    rimLight = Color(0xB3FFFFFF),
    lagoon = Color(0xFF3FE0D0),
    lotus = Color(0xFFFF6FAE),
    isDark = false,
)

val LocalNovaColors = staticCompositionLocalOf { NovaDarkColors }
