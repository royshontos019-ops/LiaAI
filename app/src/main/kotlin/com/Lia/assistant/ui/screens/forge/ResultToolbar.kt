package com.Lia.assistant.ui.screens.forge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme

/**
 * The glass pill over a finished page, plus a small handle that is always there to bring it back.
 * The screen hides the pill after 4 seconds.
 */
@Composable
fun ResultToolbar(
    visible: Boolean,
    onToggle: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onBrowser: () -> Unit,
    onEdit: () -> Unit,
    onRebuild: () -> Unit,
    onMyWebsites: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NovaTheme.colors
    var menu by remember { mutableStateOf(false) }

    Box(modifier) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = NovaSpacing.sm),
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.surfaceGlass)
                    .border(1.dp, colors.surfaceBorder, RoundedCornerShape(28.dp))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                ToolButton(Icons.Filled.Share, "Share", onShare)
                ToolButton(Icons.Filled.Save, "Save a copy", onSave)
                ToolButton(Icons.Filled.OpenInBrowser, "Open in browser", onBrowser)
                ToolButton(Icons.Filled.Edit, "Change with text", onEdit)
                Box {
                    ToolButton(Icons.Filled.MoreVert, "More") { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Build again") },
                            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                            onClick = { menu = false; onRebuild() },
                        )
                        DropdownMenuItem(
                            text = { Text("My websites") },
                            leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                            onClick = { menu = false; onMyWebsites() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = colors.error) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        }

        // Always visible: brings the toolbar back.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(NovaSpacing.md)
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.surfaceGlass)
                .border(1.dp, colors.surfaceBorder, CircleShape)
                .clickable(role = Role.Button, onClickLabel = if (visible) "Hide tools" else "Show tools", onClick = onToggle)
                .semantics { contentDescription = "Website tools" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Menu, contentDescription = null, tint = colors.textPrimary)
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = NovaTheme.colors.textPrimary)
    }
}
