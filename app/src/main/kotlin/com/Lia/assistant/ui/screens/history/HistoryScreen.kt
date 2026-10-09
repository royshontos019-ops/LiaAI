package com.Lia.assistant.ui.screens.history

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.Lia.assistant.data.ConversationStore
import com.Lia.assistant.data.ConversationSummary
import com.Lia.assistant.data.NovaAppState
import com.Lia.assistant.ui.components.NovaConfirmDialog
import com.Lia.assistant.ui.components.NovaEmptyState
import com.Lia.assistant.ui.components.NovaIconButton
import com.Lia.assistant.ui.components.NovaOrb
import com.Lia.assistant.ui.components.NovaTextField
import com.Lia.assistant.ui.components.NovaTopBar
import com.Lia.assistant.ui.theme.LocalBottomBarInset
import com.Lia.assistant.ui.theme.NovaShapes
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.ui.theme.depthSurface
import com.Lia.assistant.ui.theme.nightSky
import com.Lia.assistant.voice.NovaOrbState
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Saved conversations, newest first. A floating glass search bar sits on top; rows squash and tilt
 * as they scroll off the top. Tap a row to open it, the bin to delete it.
 */
@Composable
fun HistoryScreen(
    appState: NovaAppState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NovaTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<ConversationSummary>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ConversationSummary?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) { ConversationStore.list(context) }
    }

    val all = items
    val shown = remember(all, query) {
        when {
            all == null -> emptyList()
            query.isBlank() -> all
            else -> all.filter {
                it.title.contains(query, ignoreCase = true) || it.preview.contains(query, ignoreCase = true)
            }
        }
    }

    Box(modifier.fillMaxSize().nightSky(tilt = true)) {
        Column(Modifier.fillMaxSize()) {
            NovaTopBar(
                title = "History",
                onBack = onBack,
                actions = {
                    if (!all.isNullOrEmpty()) {
                        NovaIconButton(
                            icon = Icons.Filled.Delete,
                            contentDescription = "Delete all conversations",
                            onClick = { confirmClear = true },
                        )
                    }
                },
            )

            when {
                all == null -> Unit
                all.isEmpty() -> EmptyHistory(
                    assistantName = appState.assistantName,
                    saving = remember { ConversationStore.isSavingEnabled(context) },
                    onNewChat = onNewChat,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                else -> {
                    NovaTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search conversations",
                        leadingIcon = Icons.Filled.Search,
                        modifier = Modifier.padding(horizontal = NovaSpacing.xl, vertical = NovaSpacing.sm),
                    )
                    if (shown.isEmpty()) {
                        Text(
                            text = "Nothing matches “${query.trim()}”",
                            style = NovaTheme.type.body,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(NovaSpacing.xl),
                        )
                    } else {
                        val listState = rememberLazyListState()
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = NovaSpacing.xl,
                                end = NovaSpacing.xl,
                                top = NovaSpacing.md,
                                bottom = NovaSpacing.md + LocalBottomBarInset.current,
                            ),
                            verticalArrangement = Arrangement.spacedBy(NovaSpacing.sm),
                        ) {
                            items(shown, key = { it.id }) { conversation ->
                                HistoryRow(
                                    conversation = conversation,
                                    listState = listState,
                                    reduced = appState.reducedMotion,
                                    onClick = { onOpen(conversation.id) },
                                    onDelete = { deleteTarget = conversation },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        NovaConfirmDialog(
            title = "Delete this conversation?",
            message = "“${target.title}” will be removed from this phone.",
            confirmText = "Delete",
            onConfirm = {
                deleteTarget = null
                items = items?.filterNot { it.id == target.id }
                scope.launch(Dispatchers.IO) { ConversationStore.delete(context, target.id) }
            },
            onDismiss = { deleteTarget = null },
        )
    }

    if (confirmClear) {
        NovaConfirmDialog(
            title = "Delete all history?",
            message = "Every saved conversation will be removed from this phone. This can't be undone.",
            confirmText = "Delete all",
            onConfirm = {
                confirmClear = false
                items = emptyList()
                scope.launch(Dispatchers.IO) { ConversationStore.deleteAll(context) }
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun EmptyHistory(
    assistantName: String,
    saving: Boolean,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NovaOrb(state = NovaOrbState.IDLE, diameter = 120.dp, modifier = Modifier.alpha(0.5f))
        NovaEmptyState(
            title = "No conversations yet",
            message = if (saving) {
                "Your chats with $assistantName will show up here."
            } else {
                "Saving is switched off. You can turn it on in Privacy."
            },
            actionLabel = "Start a chat",
            onAction = onNewChat,
        )
    }
}

@Composable
private fun HistoryRow(
    conversation: ConversationSummary,
    listState: LazyListState,
    reduced: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = NovaTheme.colors
    val shape = NovaShapes.medium
    val whenText = remember(conversation.updatedAtMillis) {
        DateUtils.getRelativeTimeSpanString(
            conversation.updatedAtMillis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                if (!reduced) {
                    val info = listState.layoutInfo
                    val item = info.visibleItemsInfo.firstOrNull { it.key == conversation.id }
                    if (item != null) {
                        // How much of the row has already slid above the top edge (0 to 1).
                        val hidden = (info.viewportStartOffset - item.offset).coerceAtLeast(0)
                        val progress = (hidden / item.size.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        cameraDistance = 12f * density
                        rotationX = 35f * progress
                        scaleX = 1f - 0.06f * progress
                        scaleY = 1f - 0.25f * progress
                        alpha = 1f - 0.5f * progress
                    }
                }
            }
            .depthSurface(shape, 6.dp)
            .clip(shape)
            .background(colors.surfaceGlass)
            .border(1.dp, colors.surfaceBorder, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(start = NovaSpacing.lg, top = NovaSpacing.sm, bottom = NovaSpacing.sm, end = NovaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = conversation.title,
                style = NovaTheme.type.title,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (conversation.preview.isNotBlank()) {
                Text(
                    text = conversation.preview,
                    style = NovaTheme.type.caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(text = whenText, style = NovaTheme.type.caption, color = colors.textTertiary)
        }
        NovaIconButton(
            icon = Icons.Filled.Delete,
            contentDescription = "Delete conversation",
            onClick = onDelete,
        )
    }
}
