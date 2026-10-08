package com.Lia.assistant.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme

/** Three dots that pulse one after another. With reduced motion they simply stay on. */
@Composable
fun NovaTypingDots(
    modifier: Modifier = Modifier,
    contentDescription: String = "Assistant is typing",
) {
    val color = NovaTheme.colors.textSecondary
    val reduced = NovaTheme.reducedMotion
    val transition = rememberInfiniteTransition(label = "typingDots")
    val a0 by transition.dotAlpha(0)
    val a1 by transition.dotAlpha(1)
    val a2 by transition.dotAlpha(2)
    val alphas = if (reduced) listOf(0.7f, 0.7f, 0.7f) else listOf(a0, a1, a2)

    Row(
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(NovaSpacing.xs + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (alpha in alphas) {
            Canvas(Modifier.size(8.dp)) { drawCircle(color = color.copy(alpha = alpha)) }
        }
    }
}

@Composable
private fun androidx.compose.animation.core.InfiniteTransition.dotAlpha(index: Int) = animateFloat(
    initialValue = 0.3f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 500, easing = LinearEasing),
        repeatMode = RepeatMode.Reverse,
        initialStartOffset = StartOffset(index * 160),
    ),
    label = "dot$index",
)
