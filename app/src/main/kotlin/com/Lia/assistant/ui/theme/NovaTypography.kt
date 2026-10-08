package com.Lia.assistant.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.Lia.assistant.R

data class NovaFontFamilies(
    val serif: FontFamily,
    val sans: FontFamily,
    val hasManrope: Boolean,
) {
    companion object {
        /** Used before fonts are loaded and in tests: the system serif and sans-serif. */
        val System = NovaFontFamilies(FontFamily.Serif, FontFamily.SansSerif, hasManrope = false)
    }
}

/**
 * Instrument Serif (regular + italic) is bundled in res/font. Manrope is looked up by name
 * (res/font/manrope.ttf, the variable font): if the file has not been added yet the system
 * sans-serif is used, so the app builds and runs either way.
 */
object NovaFonts {
    const val MANROPE_RESOURCE = "manrope"
    val MANROPE_WEIGHTS = listOf(400, 500, 600, 700, 800)

    @OptIn(ExperimentalTextApi::class)
    fun load(context: Context): NovaFontFamilies {
        val serif = FontFamily(
            Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
            Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
        )
        val manrope = context.resources.getIdentifier(MANROPE_RESOURCE, "font", context.packageName)
        if (manrope == 0) return NovaFontFamilies(serif, FontFamily.SansSerif, hasManrope = false)

        val sans = FontFamily(
            MANROPE_WEIGHTS.map { w ->
                Font(
                    resId = manrope,
                    weight = FontWeight(w),
                    style = FontStyle.Normal,
                    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
                )
            },
        )
        return NovaFontFamilies(serif, sans, hasManrope = true)
    }
}

/**
 * Serif for display, headline and the assistant's "voice" lines; Manrope for everything you read
 * or tap. title 17/24 semibold, body 15/22, label 13/18, caption 12/16.
 */
data class NovaTypeScale(
    val display: TextStyle,
    val headline: TextStyle,
    val voice: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
)

fun buildNovaTypeScale(fonts: NovaFontFamilies): NovaTypeScale = NovaTypeScale(
    display = TextStyle(fontFamily = fonts.serif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 48.sp),
    headline = TextStyle(fontFamily = fonts.serif, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp),
    voice = TextStyle(
        fontFamily = fonts.serif, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Italic,
        fontSize = 22.sp, lineHeight = 30.sp,
    ),
    title = TextStyle(fontFamily = fonts.sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
    body = TextStyle(fontFamily = fonts.sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    label = TextStyle(fontFamily = fonts.sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    caption = TextStyle(fontFamily = fonts.sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
)

fun NovaTypeScale.toMaterial() = Typography(
    displayLarge = display, displayMedium = display, displaySmall = display,
    headlineLarge = headline, headlineMedium = headline, headlineSmall = headline,
    titleLarge = title, titleMedium = title, titleSmall = title,
    bodyLarge = body, bodyMedium = body, bodySmall = caption,
    labelLarge = label, labelMedium = label, labelSmall = caption,
)

val LocalNovaTypography = staticCompositionLocalOf { buildNovaTypeScale(NovaFontFamilies.System) }
