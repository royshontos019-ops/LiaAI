package com.Lia.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.Lia.assistant.ui.theme.NovaMinTouchTarget
import com.Lia.assistant.ui.theme.NovaMotion
import com.Lia.assistant.ui.theme.NovaShapes
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme

/**
 * A row (or rows) of pill chips where exactly one is selected. [columns] chips per row.
 * Marigold fill = selected. Each chip is at least 48 dp tall.
 */
@Composable
fun <T> NovaChoiceChips(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NovaSpacing.sm),
    ) {
        options.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NovaSpacing.sm),
            ) {
                rowItems.forEach { option ->
                    NovaChip(
                        text = label(option),
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun NovaChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NovaTheme.colors
    val background by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.surfaceRaised,
        animationSpec = tween(NovaMotion.FAST_MS),
        label = "chipBackground",
    )
    val foreground = if (selected) colors.onAccent else colors.textPrimary
    val borderColor = if (selected) colors.accent else colors.surfaceBorder

    Box(
        modifier = modifier
            .heightIn(min = NovaMinTouchTarget)
            .clip(NovaShapes.pill)
            .background(background)
            .border(1.dp, borderColor, NovaShapes.pill)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = NovaSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = NovaTheme.type.label,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
