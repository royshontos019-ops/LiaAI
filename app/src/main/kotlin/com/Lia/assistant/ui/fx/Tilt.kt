package com.Lia.assistant.ui.fx

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.Lia.assistant.ui.theme.NovaTheme

private val NoTilt = mutableStateOf(Offset.Zero)

/** The phone's tilt, -1..1 on both axes, shared by every screen. Zero when nothing provides it. */
val LocalTilt = compositionLocalOf<State<Offset>> { NoTilt }

/**
 * Smoothed tilt from the accelerometer. Provide it once near the root so the whole app shares a
 * single sensor listener. Off (and zero) when [enabled] is false.
 */
@Composable
fun rememberDeviceTilt(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(enabled) {
        val manager = if (enabled) context.getSystemService(SensorManager::class.java) else null
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val targetX = (-event.values[0] / 9.81f).coerceIn(-1f, 1f)
                // A phone held upright reads about 0.7 on this axis, so that is the neutral point.
                val targetY = ((event.values[1] / 9.81f - 0.7f) * 1.5f).coerceIn(-1f, 1f)
                val now = state.value
                state.value = Offset(now.x + 0.1f * (targetX - now.x), now.y + 0.1f * (targetY - now.y))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (manager != null && sensor != null) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            state.value = Offset.Zero
        }
        onDispose { manager?.unregisterListener(listener) }
    }
    return state
}

/**
 * Shifts this layer by up to [depth] as the phone tilts. Closer layers get a bigger depth.
 * Does nothing with reduced motion. Only the graphics layer changes, so nothing recomposes.
 */
@Composable
fun Modifier.tiltParallax(depth: Dp): Modifier {
    if (NovaTheme.reducedMotion) return this
    val tilt = LocalTilt.current
    return this.graphicsLayer {
        val d = depth.toPx()
        translationX = tilt.value.x * d
        translationY = tilt.value.y * d
    }
}
