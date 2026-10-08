package com.Lia.assistant.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Lia.assistant.ui.theme.NovaMinTouchTarget
import com.Lia.assistant.ui.theme.NovaMotion
import com.Lia.assistant.ui.theme.NovaShapes
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme

enum class NovaButtonStyle { PRIMARY, SECONDARY, TEXT }

/** Marigold means "you can press this". Presses shrink the button to 0.96 (not with reduced motion). */
@Composable
fun NovaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: NovaButtonStyle = NovaButtonStyle.PRIMARY,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    contentDescription: String? = null,
) {
    val colors = NovaTheme.colors
    val reducedMotion = NovaTheme.reducedMotion
    val shape = NovaShapes.pill
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) 0.96f else 1f,
        animationSpec = tween(NovaMotion.FAST_MS),
        label = "buttonScale",
    )

    val background: Color
    val foreground: Color
    when (style) {
        NovaButtonStyle.PRIMARY -> { background = colors.accent; foreground = colors.onAccent }
        NovaButtonStyle.SECONDARY -> { background = colors.surfaceRaised; foreground = colors.accent }
        NovaButtonStyle.TEXT -> { background = Color.Transparent; foreground = colors.accent }
    }

    Box(
        modifier = modifier
            .heightIn(min = NovaMinTouchTarget)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .alpha(if (enabled) 1f else 0.4f)
            .then(
                if (style == NovaButtonStyle.PRIMARY && enabled) {
                    Modifier.shadow(8.dp, shape, ambientColor = colors.accentGlow, spotColor = colors.accentGlow)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(background)
            .then(
                if (style == NovaButtonStyle.SECONDARY) Modifier.border(1.dp, colors.accent, shape) else Modifier,
            )
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = NovaSpacing.xl, vertical = NovaSpacing.md)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = foreground, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(NovaSpacing.sm))
            }
            Text(text, style = NovaTheme.type.body.copy(fontWeight = FontWeight.Bold), color = foreground)
        }
    }
}
