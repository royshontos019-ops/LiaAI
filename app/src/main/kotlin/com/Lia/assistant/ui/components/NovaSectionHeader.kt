package com.Lia.assistant.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme

/** A small quiet heading above a group of settings. Not tappable, so not marigold. */
@Composable
fun NovaSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = NovaTheme.type.label.copy(letterSpacing = 0.8.sp),
        color = NovaTheme.colors.textTertiary,
        modifier = modifier
            .padding(horizontal = NovaSpacing.xs, vertical = NovaSpacing.sm)
            .semantics { heading() },
    )
}
