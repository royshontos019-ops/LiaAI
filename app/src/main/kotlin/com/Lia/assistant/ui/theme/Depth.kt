package com.Lia.assistant.ui.theme

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Height of the floating navigation bar. Screens add it as bottom padding (or content padding)
 * so their content can run underneath the bar and still end above it.
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/** Bottom padding equal to the floating bar's height. */
@Composable
fun Modifier.bottomBarInsetPadding(): Modifier = this.padding(PaddingValues(bottom = LocalBottomBarInset.current))

internal data class Star(val x: Float, val y: Float, val radiusDp: Float, val alpha: Float)

/** Same seed, same sky. Positions are fractions of the width and height (0..1). */
internal fun generateStars(count: Int, seed: Long): List<Star> {
    val random = Random(seed)
    return List(count) {
        Star(
            x = random.nextFloat(),
            y = random.nextFloat(),
            radiusDp = 0.5f + random.nextFloat() * 1.1f,
            alpha = 0.25f + random.nextFloat() * 0.55f,
        )
    }
}

/**
 * The night sky: a gradient from the indigo top colour into the background, and (in the dark
 * theme only) a sparse field of stars. With [tilt] the stars drift a little as the phone tilts,
 * bigger stars more than small ones. Reduced motion switches the drift off.
 */
@Composable
fun Modifier.nightSky(tilt: Boolean = false): Modifier {
    val colors = NovaTheme.colors
    val reduced = NovaTheme.reducedMotion
    val stars = remember { generateStars(count = 70, seed = 7L) }
    val tiltState = rememberTilt(enabled = tilt && !reduced && colors.isDark)

    return this.drawBehind {
        drawRect(Brush.verticalGradient(listOf(colors.backgroundGradientTop, colors.background)))
        if (colors.isDark) {
            val drift = tiltState.value
            for (star in stars) {
                val radius = star.radiusDp.dp.toPx()
                val parallax = 10.dp.toPx() * star.radiusDp
                drawCircle(
                    color = colors.textPrimary.copy(alpha = star.alpha),
                    radius = radius,
                    center = Offset(
                        star.x * size.width + drift.x * parallax,
                        star.y * size.height + drift.y * parallax,
                    ),
                )
            }
        }
    }
}

/**
 * A raised surface: a coloured ambient shadow underneath and a thin rim of light along the top
 * edge. Put it BEFORE the background in the chain, so the rim is drawn over it:
 * Modifier.depthSurface(shape, 8.dp).clip(shape).background(...)
 */
@Composable
fun Modifier.depthSurface(shape: Shape, elevation: Dp = 8.dp): Modifier {
    val colors = NovaTheme.colors
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = colors.depthShadow,
            spotColor = colors.depthShadow,
        )
        .drawWithContent {
            drawContent()
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(
                outline = outline,
                brush = Brush.verticalGradient(
                    colors = listOf(colors.rimLight, Color.Transparent),
                    endY = size.height * 0.45f,
                ),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
}

/** Smoothed phone tilt in -1..1 on both axes, from the accelerometer. Zero when off or unavailable. */
@Composable
private fun rememberTilt(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(enabled) {
        if (!enabled) {
            state.value = Offset.Zero
            return@DisposableEffect onDispose {}
        }
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) return@DisposableEffect onDispose {}

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val targetX = (-event.values[0] / 9.8f).coerceIn(-1f, 1f)
                val targetY = (event.values[1] / 9.8f).coerceIn(-1f, 1f)
                val now = state.value
                state.value = Offset(now.x + 0.1f * (targetX - now.x), now.y + 0.1f * (targetY - now.y))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }
    return state
}
