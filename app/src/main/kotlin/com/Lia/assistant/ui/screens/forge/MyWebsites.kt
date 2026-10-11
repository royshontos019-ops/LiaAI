package com.Lia.assistant.ui.screens.forge

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.Lia.assistant.forge.ForgeFiles
import com.Lia.assistant.ui.components.NovaButton
import com.Lia.assistant.ui.components.NovaButtonStyle
import com.Lia.assistant.ui.components.NovaGlassCard
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import java.io.File
import java.text.DateFormat
import java.util.Date

/** The pages Lia saved, newest first. Tap one to open it; the bin deletes it (after a question). */
@Composable
fun MyWebsitesDialog(
    files: List<File>,
    onOpen: (File) -> Unit,
    onDelete: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NovaTheme.colors
    val dates = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    Dialog(onDismissRequest = onDismiss) {
        NovaGlassCard(Modifier.fillMaxWidth(), elevation = 12.dp) {
            Column {
                Text("My websites", style = NovaTheme.type.title, color = colors.textPrimary)
                Spacer(Modifier.size(NovaSpacing.sm))
                if (files.isEmpty()) {
                    Text("No websites yet. Ask Lia to build one.", style = NovaTheme.type.body, color = colors.textSecondary)
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(files, key = { it.absolutePath }) { file ->
                            val title = remember(file) { titleFor(file) }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button, onClickLabel = "Open $title") { onOpen(file) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(title, style = NovaTheme.type.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${dates.format(Date(file.lastModified()))} \u00B7 ${file.length() / 1000} KB",
                                        style = NovaTheme.type.caption,
                                        color = colors.textSecondary,
                                    )
                                }
                                Box(
                                    Modifier
                                        .size(48.dp)
                                        .clickable(role = Role.Button, onClickLabel = "Delete $title") { onDelete(file) }
                                        .semantics { contentDescription = "Delete $title" },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = colors.error)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.size(NovaSpacing.md))
                NovaButton(text = "Close", onClick = onDismiss, style = NovaButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** The page's own <title> when it has one (read from the start of the file), else the file name. */
private fun titleFor(file: File): String {
    val head = try {
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            val n = stream.read(buffer)
            if (n > 0) String(buffer, 0, n, Charsets.UTF_8) else ""
        }
    } catch (e: Exception) {
        ""
    }
    return ForgeFiles.titleOf(head) ?: file.nameWithoutExtension
}
