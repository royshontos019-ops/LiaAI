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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.Lia.assistant.ui.theme.NovaMotion
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.voice.NovaOrbState

/**
 * The pearl. Lagoon and lotus are used only here: lagoon while listening and idle, lotus while
 * speaking, a mix while thinking. Marigold while connecting, red on error. The pulse follows
 * [state]; with reduced motion it stays still.
 */
@Composable
fun NovaOrb(
    state: NovaOrbState,
    modifier: Modifier = Modifier,
    diameter: Dp = 40.dp,
    amplitude: Float = 0f,
    reducedMotion: Boolean = NovaTheme.reducedMotion,
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
    val core by animateColorAsState(target, tween(NovaMotion.SLOW_MS), label = "orbColor")

    val periodMs = when (state) {
        NovaOrbState.THINKING -> 700
        NovaOrbState.SPEAKING -> 500
        NovaOrbState.LISTENING -> 1400
        NovaOrbState.CONNECTING -> 900
        NovaOrbState.ERROR -> 2000
        NovaOrbState.IDLE -> 2600
    }
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Reverse),
        label = "orbPulse",
    )
    val p = if (reducedMotion) 0.5f else pulse
    val scale = 0.82f + 0.1f * p + 0.12f * amplitude.coerceIn(0f, 1f)
    val highlight = colors.textPrimary.copy(alpha = 0.35f)
    val description = when (state) {
        NovaOrbState.IDLE -> "Assistant is idle"
        NovaOrbState.LISTENING -> "Assistant is listening"
        NovaOrbState.THINKING -> "Assistant is thinking"
        NovaOrbState.SPEAKING -> "Assistant is speaking"
        NovaOrbState.CONNECTING -> "Assistant is connecting"
        NovaOrbState.ERROR -> "Assistant has a problem"
    }

    Canvas(modifier.size(diameter).semantics { contentDescription = description }) {
        val radius = this.size.minDimension / 2f * scale
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(core.copy(alpha = 0.55f), core.copy(alpha = 0.18f), Color.Transparent),
                center = center,
                radius = radius * 1.25f,
            ),
            radius = radius * 1.25f,
            center = center,
        )
        drawCircle(color = core, radius = radius * 0.62f, center = center)
        drawCircle(color = highlight, radius = radius * 0.22f, center = center - Offset(radius * 0.18f, radius * 0.18f))
    }
}
