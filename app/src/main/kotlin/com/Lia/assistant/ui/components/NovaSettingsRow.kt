package com.Lia.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.Lia.assistant.ui.theme.NovaShapes
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme

/**
 * One line of a settings list: optional icon, title, subtitle, a value on the right, and either a
 * switch ([switchChecked] + [onSwitchChange]) or a chevron when [onClick] is set.
 * The whole row is one touch target (at least 56 dp tall) and one item for screen readers.
 */
@Composable
fun NovaSettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    value: String? = null,
    switchChecked: Boolean? = null,
    onSwitchChange: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = NovaTheme.colors
    val hasSwitch = switchChecked != null && onSwitchChange != null

    val interaction = when {
        hasSwitch -> Modifier.toggleable(
            value = switchChecked == true,
            role = Role.Switch,
            onValueChange = { onSwitchChange?.invoke(it) },
        )
        onClick != null -> Modifier.clickable(role = Role.Button, onClick = onClick)
        else -> Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(NovaShapes.small)
            .then(interaction)
            .heightIn(min = 56.dp)
            .padding(horizontal = NovaSpacing.md, vertical = NovaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.surfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(NovaSpacing.md))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(title, style = NovaTheme.type.title, color = colors.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = NovaTheme.type.caption, color = colors.textSecondary)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(NovaSpacing.sm))
            Text(value, style = NovaTheme.type.body, color = colors.textSecondary)
        }
        if (hasSwitch) {
            Spacer(Modifier.width(NovaSpacing.sm))
            Switch(
                checked = switchChecked == true,
                onCheckedChange = null, // the whole row toggles
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onAccent,
                    checkedTrackColor = colors.accent,
                    checkedBorderColor = colors.accent,
                    uncheckedThumbColor = colors.textSecondary,
                    uncheckedTrackColor = colors.surfaceRaised,
                    uncheckedBorderColor = colors.surfaceBorder,
                ),
            )
        } else if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.textTertiary,
            )
        }
    }
}
