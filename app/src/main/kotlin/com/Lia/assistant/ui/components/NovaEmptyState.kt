package com.Lia.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme

/** Friendly placeholder for a screen with nothing in it yet. The title is a serif "voice" line. */
@Composable
fun NovaEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = NovaTheme.colors
    Column(
        modifier = modifier.padding(NovaSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(colors.surfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(NovaSpacing.lg))
        }
        Text(title, style = NovaTheme.type.voice, color = colors.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(NovaSpacing.sm))
        Text(message, style = NovaTheme.type.body, color = colors.textSecondary, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(NovaSpacing.xl))
            NovaButton(actionLabel, onAction)
        }
    }
}
