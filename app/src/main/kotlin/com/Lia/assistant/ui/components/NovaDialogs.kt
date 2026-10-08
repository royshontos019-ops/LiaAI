package com.Lia.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.Lia.assistant.ui.theme.NovaShapes
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.ui.theme.depthSurface

/** The shell all dialogs share: raised indigo panel, serif title, buttons on the right. */
@Composable
fun NovaDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = NovaTheme.colors
    val shape = NovaShapes.large
    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = modifier
                .widthIn(max = 360.dp)
                .depthSurface(shape, 16.dp)
                .clip(shape)
                .background(colors.surfaceRaised)
                .border(1.dp, colors.surfaceBorder, shape)
                .padding(NovaSpacing.xl),
        ) {
            Text(
                text = title,
                style = NovaTheme.type.headline.copy(fontSize = 24.sp, lineHeight = 30.sp),
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(NovaSpacing.md))
            content()
            if (confirmText != null || dismissText != null) {
                Spacer(Modifier.height(NovaSpacing.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (dismissText != null) {
                        NovaButton(dismissText, onClick = onDismiss ?: onDismissRequest, style = NovaButtonStyle.TEXT)
                    }
                    if (confirmText != null && onConfirm != null) {
                        Spacer(Modifier.width(NovaSpacing.sm))
                        NovaButton(confirmText, onClick = onConfirm)
                    }
                }
            }
        }
    }
}

/** A yes / no question. */
@Composable
fun NovaConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = "Cancel",
) {
    NovaDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmText = confirmText,
        onConfirm = onConfirm,
        dismissText = dismissText,
        onDismiss = onDismiss,
    ) {
        Text(message, style = NovaTheme.type.body, color = NovaTheme.colors.textSecondary)
    }
}

/**
 * Pick one option from a list (voice, language, personality...). Choosing an option calls
 * [onSelect] and closes the dialog. Each row is a radio button, at least 56 dp tall.
 */
@Composable
fun <T> NovaPickerDialog(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    optionDescription: (T) -> String? = { null },
) {
    val colors = NovaTheme.colors
    NovaDialog(onDismissRequest = onDismiss, title = title, dismissText = "Close", onDismiss = onDismiss) {
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(options) { option ->
                val isSelected = option == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(NovaShapes.small)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = {
                                onSelect(option)
                                onDismiss()
                            },
                        )
                        .heightIn(min = 56.dp)
                        .padding(horizontal = NovaSpacing.sm, vertical = NovaSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = null, // the whole row is the touch target
                        colors = RadioButtonDefaults.colors(
                            selectedColor = colors.accent,
                            unselectedColor = colors.textTertiary,
                        ),
                    )
                    Spacer(Modifier.width(NovaSpacing.md))
                    Column {
                        Text(optionLabel(option), style = NovaTheme.type.title, color = colors.textPrimary)
                        optionDescription(option)?.let {
                            Text(it, style = NovaTheme.type.caption, color = colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
