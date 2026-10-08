package com.Lia.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.Lia.assistant.data.AssistantBrand
import com.Lia.assistant.ui.theme.NovaMotion
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.voice.NovaOrbState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Look of the orb. A personality picks one. */
enum class NovaOrbStyle { NOVA, AURORA, PLASMA, GLASS, ENERGY, MINIMAL, ARCHER }

private const val TWO_PI = (2.0 * PI).toFloat()
private const val ARCHER_POINTS = 400

/** Points on a unit sphere, spread along a golden spiral. x, y, z per point. */
private fun goldenSpiralSphere(count: Int): FloatArray {
    val out = FloatArray(count * 3)
    val golden = (PI * (3.0 - sqrt(5.0))).toFloat()
    for (i in 0 until count) {
        val y = 1f - 2f * (i + 0.5f) / count
        val r = sqrt(max(0f, 1f - y * y))
        val theta = golden * i
        out[i * 3] = cos(theta) * r
        out[i * 3 + 1] = y
        out[i * 3 + 2] = sin(theta) * r
    }
    return out
}

/**
 * The pearl. Lagoon and lotus are used only here: lagoon while listening and idle, lotus while
 * speaking, a mix while thinking. Marigold while connecting, red on error. With reduced motion
 * it stays still. This is also the fallback for the 3D orb on Android 12 and older.
 */
@Composable
fun NovaOrb(
    state: NovaOrbState,
    modifier: Modifier = Modifier,
    diameter: Dp = 40.dp,
    amplitude: Float = 0f,
    reducedMotion: Boolean = NovaTheme.reducedMotion,
    style: NovaOrbStyle = NovaOrbStyle.NOVA,
    name: String = AssistantBrand.NAME,
) {
    val colors = NovaTheme.colors
    val target = when (state) {
        NovaOrbState.IDLE -> lerp(colors.lagoon, colors.textSecondary, 0.55f)
        NovaOrbState.LISTENING -> colors.lagoon
        NovaOrbState.THINKING -> lerp(colors.lagoon, colors.lotus, 0.5f)
        NovaOrbState.SPEAKING -> colors.lotus
        NovaOrbState.CONNECTING -> colors.accent
        NovaOrbState.ERROR -> colors.error
    }
    val base by animateColorAsState(target, tween(NovaMotion.SLOW_MS), label = "orbColor")
    val tone = when (style) {
        NovaOrbStyle.AURORA -> lerp(base, colors.lagoon, 0.5f)
        NovaOrbStyle.PLASMA -> lerp(base, colors.lotus, 0.5f)
        NovaOrbStyle.ENERGY -> lerp(base, colors.accent, 0.4f)
        NovaOrbStyle.GLASS -> lerp(base, Color.White, 0.25f)
        else -> base
    }
    val amp = amplitude.coerceIn(0f, 1f)

    val particleCount = when {
        state == NovaOrbState.ERROR -> 0
        style == NovaOrbStyle.MINIMAL || style == NovaOrbStyle.ARCHER -> 0
        style == NovaOrbStyle.ENERGY -> 8
        style == NovaOrbStyle.PLASMA -> 6
        style == NovaOrbStyle.GLASS -> 3
        else -> 5
    }
    val glowScale = when (style) {
        NovaOrbStyle.AURORA -> 2.3f
        NovaOrbStyle.ENERGY -> 2.3f
        NovaOrbStyle.MINIMAL -> 1.6f
        else -> 2.0f
    }
    val coreAlpha = if (style == NovaOrbStyle.GLASS) 0.6f else 1f

    // Breathing (4.2 s) and the listening rings use fixed periods, so plain infinite transitions fit.
    val transition = rememberInfiniteTransition(label = "orb")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "orbBreath",
    )
    val ring by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbRing",
    )

    // Rotation changes speed with the state (9 s, 2.6 s thinking, 1.1 s connecting). Adding up the
    // angle frame by frame keeps it smooth when the speed changes.
    val periodSeconds = rememberUpdatedState(
        when (state) {
            NovaOrbState.THINKING -> 2.6f
            NovaOrbState.CONNECTING -> 1.1f
            else -> 9f
        } / (if (style == NovaOrbStyle.ENERGY) 1.3f else 1f),
    )
    var rotation by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            rotation = (rotation + 360f * dt / periodSeconds.value) % 360f
        }
    }

    val points = remember { goldenSpiralSphere(ARCHER_POINTS) }
    val highlight = colors.textPrimary.copy(alpha = 0.35f)
    val description = "$name status: ${state.name.lowercase()}"

    Canvas(modifier.size(diameter).semantics { contentDescription = description }) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val half = this.size.minDimension / 2f
        val b = if (reducedMotion) 0f else sin(breath * TWO_PI)
        val rotRad = rotation * TWO_PI / 360f

        if (style == NovaOrbStyle.ARCHER) {
            val sphereR = half * 0.80f * (1f + 0.035f * b)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tone.copy(alpha = 0.22f), Color.Transparent),
                    center = center,
                    radius = sphereR * 1.1f,
                ),
                radius = sphereR * 1.1f,
                center = center,
            )
            val sy = sin(rotRad * 0.6f)
            val cy = cos(rotRad * 0.6f)
            val sx = sin(0.35f)
            val cx = cos(0.35f)
            val dot = 1.2.dp.toPx()
            for (i in 0 until ARCHER_POINTS) {
                val x0 = points[i * 3]
                val y0 = points[i * 3 + 1]
                val z0 = points[i * 3 + 2]
                val jitter = 1f + amp * 0.14f * sin(i * 12.9898f + rotRad * 3f)
                val x1 = x0 * cy + z0 * sy
                val z1 = -x0 * sy + z0 * cy
                val y2 = y0 * cx - z1 * sx
                val z2 = y0 * sx + z1 * cx
                val depth = (z2 + 1f) / 2f
                drawCircle(
                    color = tone.copy(alpha = 0.12f + 0.88f * depth),
                    radius = dot * (0.6f + 1.1f * depth),
                    center = Offset(center.x + x1 * sphereR * jitter, center.y + y2 * sphereR * jitter),
                )
            }
            return@Canvas
        }

        val r = half * 0.46f * (1f + 0.035f * b) * (1f + 0.12f * amp)

        // Outer glow.
        val glowR = r * glowScale
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(tone.copy(alpha = 0.55f), tone.copy(alpha = 0.18f), Color.Transparent),
                center = center,
                radius = glowR,
            ),
            radius = glowR,
            center = center,
        )

        // Two expanding rings while listening.
        if (state == NovaOrbState.LISTENING) {
            for (k in 0..1) {
                val p = if (reducedMotion) 0.35f + 0.4f * k else (ring + 0.5f * k) % 1f
                drawCircle(
                    color = tone.copy(alpha = (1f - p) * 0.5f),
                    radius = r * (1f + 0.9f * p),
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }

        // One pulse ring on error.
        if (state == NovaOrbState.ERROR) {
            val p = if (reducedMotion) 0.4f else ring
            drawCircle(
                color = tone.copy(alpha = (1f - p) * 0.6f),
                radius = r * (1f + 0.6f * p),
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        // A 100 degree arc turning around the orb while connecting.
        if (state == NovaOrbState.CONNECTING) {
            val arcR = r * 1.4f
            drawArc(
                color = tone,
                startAngle = rotation,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(center.x - arcR, center.y - arcR),
                size = Size(arcR * 2f, arcR * 2f),
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // Orbiting particles.
        if (particleCount > 0) {
            val dot = max(r * 0.09f, 1.5.dp.toPx())
            for (i in 0 until particleCount) {
                val angle = rotRad + i * TWO_PI / particleCount
                val orbit = r * (1.5f + 0.08f * sin(i * 1.7f) + 0.04f * b)
                drawCircle(
                    color = tone.copy(alpha = 0.9f),
                    radius = dot,
                    center = Offset(center.x + cos(angle) * orbit, center.y + sin(angle) * orbit),
                )
            }
        }

        // Core.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    lerp(tone, Color.White, 0.35f).copy(alpha = coreAlpha),
                    tone.copy(alpha = coreAlpha),
                ),
                center = center,
                radius = r,
            ),
            radius = r,
            center = center,
        )

        if (style == NovaOrbStyle.GLASS) {
            val arcR = r * 0.82f
            drawArc(
                color = Color.White.copy(alpha = 0.55f),
                startAngle = 200f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(center.x - arcR, center.y - arcR),
                size = Size(arcR * 2f, arcR * 2f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.25f),
                radius = r,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
        } else {
            drawCircle(color = highlight, radius = r * 0.35f, center = center - Offset(r * 0.28f, r * 0.28f))
        }
    }
}
