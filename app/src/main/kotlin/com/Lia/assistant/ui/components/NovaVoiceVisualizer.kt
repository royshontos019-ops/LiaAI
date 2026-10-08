package com.Lia.assistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.Lia.assistant.ui.theme.NovaMotion
import com.Lia.assistant.ui.theme.NovaTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * A row of soft bars that rise and fall with the voice level (0..1). Lagoon to lotus: these two
 * colours belong to the orb and its visualizer. With reduced motion the bars follow the level
 * directly and do not ripple.
 */
@Composable
fun NovaVoiceVisualizer(
    amplitude: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 24,
    contentDescription: String = "Voice level",
) {
    val colors = NovaTheme.colors
    val reduced = NovaTheme.reducedMotion
    val level by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 1f),
        animationSpec = if (reduced) snap() else tween(NovaMotion.FAST_MS),
        label = "voiceLevel",
    )
    val transition = rememberInfiniteTransition(label = "voiceWave")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "voicePhase",
    )
    val phase = if (reduced) 0f else wave

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val gap = 4.dp.toPx()
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        val brush = Brush.horizontalGradient(
            colors = listOf(colors.lagoon, colors.lotus),
            startX = 0f,
            endX = size.width,
        )
        for (i in 0 until barCount) {
            val ripple = if (reduced) 1f else 0.55f + 0.45f * abs(sin(phase + i * 0.55f))
            val height = (size.height * (0.1f + 0.9f * level * ripple)).coerceIn(barWidth, size.height)
            drawRoundRect(
                brush = brush,
                topLeft = Offset(i * (barWidth + gap), (size.height - height) / 2f),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}
