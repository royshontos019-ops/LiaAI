package com.Lia.assistant.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.Lia.assistant.ui.theme.NovaMinTouchTarget
import com.Lia.assistant.ui.theme.NovaTheme

/** An icon you can tap. Always at least 48 dp, and always needs a description for screen readers. */
@Composable
fun NovaIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = NovaTheme.colors.textSecondary,
    enabled: Boolean = true,
    size: Dp = NovaMinTouchTarget,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(maxOf(size, NovaMinTouchTarget))) {
        Icon(icon, contentDescription = contentDescription, tint = if (enabled) tint else tint.copy(alpha = 0.38f))
    }
}
