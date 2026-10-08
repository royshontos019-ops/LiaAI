package com.Lia.assistant.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.Lia.assistant.data.ThemeMode

/** True when animation should be skipped: the app setting, or the system "remove animations". */
val LocalNovaReducedMotion = staticCompositionLocalOf { false }

/** Short access to the tokens: NovaTheme.colors, NovaTheme.type, NovaTheme.reducedMotion. */
object NovaTheme {
    val colors: NovaColorScheme
        @Composable @ReadOnlyComposable get() = LocalNovaColors.current

    val type: NovaTypeScale
        @Composable @ReadOnlyComposable get() = LocalNovaTypography.current

    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalNovaReducedMotion.current
}

/**
 * The one theme of the app. No dynamic colour: Lia always looks like Lia.
 * Maps the Nova tokens onto a Material3 colour scheme, makes the status and navigation bars
 * transparent with readable icons, and provides the tokens to everything below.
 */
@Suppress("DEPRECATION")
@Composable
fun NovaTheme(
    mode: ThemeMode = ThemeMode.DARK,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = if (dark) NovaDarkColors else NovaLightColors
    val context = LocalContext.current
    val fonts = remember(context) { NovaFonts.load(context) }
    val type = remember(fonts) { buildNovaTypeScale(fonts) }
    val systemReduced = remember(context) { systemAnimationsAreOff(context) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(
        LocalNovaColors provides colors,
        LocalNovaTypography provides type,
        LocalNovaReducedMotion provides (reducedMotion || systemReduced),
    ) {
        MaterialTheme(
            colorScheme = novaMaterialScheme(colors),
            typography = type.toMaterial(),
            shapes = NovaShapes.toMaterial(),
            content = content,
        )
    }
}

/** Material3 needs a colour scheme for its own widgets (dialogs, text fields); fill it from the tokens. */
internal fun novaMaterialScheme(c: NovaColorScheme): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        primaryContainer = c.surfaceRaised,
        onPrimaryContainer = c.textPrimary,
        secondary = c.accentSecondary,
        onSecondary = c.onAccent,
        secondaryContainer = c.surfaceRaised,
        onSecondaryContainer = c.textPrimary,
        tertiary = c.accent,
        onTertiary = c.onAccent,
        background = c.background,
        onBackground = c.textPrimary,
        surface = c.surface,
        onSurface = c.textPrimary,
        surfaceVariant = c.surfaceRaised,
        onSurfaceVariant = c.textSecondary,
        surfaceTint = c.accent,
        surfaceContainerLowest = c.background,
        surfaceContainerLow = c.surface,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surfaceRaised,
        surfaceContainerHighest = c.surfaceRaised,
        outline = c.textTertiary,
        outlineVariant = c.surfaceBorder,
        error = c.error,
        onError = c.onAccent,
        errorContainer = c.surfaceRaised,
        onErrorContainer = c.error,
        scrim = c.depthShadow,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun systemAnimationsAreOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
