package com.Lia.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.Lia.assistant.ui.theme.NovaTheme

/** A round avatar with the person's initial, floating over a soft marigold glow. */
@Composable
fun NovaAvatar(
    initial: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 104.dp,
) {
    val colors = NovaTheme.colors
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val radius = this.size.minDimension / 2f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(colors.accentGlow, Color.Transparent),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                    )
                },
        )
        Box(
            modifier = Modifier
                .size(diameter * 0.65f)
                .clip(CircleShape)
                .background(colors.surfaceRaised)
                .border(1.dp, colors.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = initial, style = NovaTheme.type.headline, color = colors.accent)
        }
    }
}
