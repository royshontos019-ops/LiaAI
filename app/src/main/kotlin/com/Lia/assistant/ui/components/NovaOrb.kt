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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.Lia.assistant.voice.NovaOrbState

/** Small animated orb. Colour and pulse speed follow [state]; [reducedMotion] freezes the pulse. */
@Composable
fun NovaOrb(
    state: NovaOrbState,
    modifier: Modifier = Modifier,
    diameter: Dp = 40.dp,
    amplitude: Float = 0f,
    reducedMotion: Boolean = false,
) {
    val target = when (state) {
        NovaOrbState.IDLE -> Color(0xFF8E9AD6)
        NovaOrbState.LISTENING -> Color(0xFF3DDBC1)
        NovaOrbState.THINKING -> Color(0xFFB388FF)
        NovaOrbState.SPEAKING -> Color(0xFFFF7EB6)
        NovaOrbState.CONNECTING -> Color(0xFFFFC857)
        NovaOrbState.ERROR -> Color(0xFFFF6B6B)
    }
    val core by animateColorAsState(target, tween(400), label = "orbColor")

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

    Canvas(modifier.size(diameter)) {
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
        drawCircle(color = Color.White.copy(alpha = 0.35f), radius = radius * 0.22f, center = center - Offset(radius * 0.18f, radius * 0.18f))
    }
}
