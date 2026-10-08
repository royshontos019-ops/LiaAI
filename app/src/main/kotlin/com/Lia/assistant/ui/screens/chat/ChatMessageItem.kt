package com.Lia.assistant.ui.screens.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.Lia.assistant.ui.theme.NovaShapes
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.ui.theme.depthSurface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.Lia.assistant.ui.components.NovaIconButton
import com.Lia.assistant.ui.components.NovaMessageBubble
import kotlinx.coroutines.delay
import com.Lia.assistant.ui.components.ChatMessage as BubbleMessage

/**
 * One row of the chat: the design-system bubble plus copy / retry / read-aloud under assistant
 * replies. When [reveal] is on, a new reply appears word by word (20 ms per word) with a caret.
 */
@Composable
fun ChatMessageItem(
    message: ChatMessage,
    reveal: Boolean,
    isSpeaking: Boolean,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    onSpeak: () -> Unit,
    onRevealProgress: () -> Unit,
    onRevealFinished: () -> Unit,
    modifier: Modifier = Modifier,
    /** True for a message that just arrived: it rises into place. */
    rise: Boolean = false,
    /** When given, a reply that started the Forge shows a small preview chip that calls this. */
    onOpenForge: (() -> Unit)? = null,
) {
    val reduced = NovaTheme.reducedMotion
    val settle = remember(message.id) { Animatable(if (rise && !reduced) 0f else 1f) }
    LaunchedEffect(message.id) {
        if (settle.value < 1f) settle.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }

    val tokens = remember(message.text) { RevealText.tokens(message.text) }
    val animating = message.animate && reveal && !message.isUser
    var shown by remember(message.id) { mutableIntStateOf(if (animating) 0 else tokens.size) }

    LaunchedEffect(message.id, message.animate, reveal) {
        if (!message.animate) return@LaunchedEffect
        if (reveal) {
            while (shown < tokens.size) {
                delay(RevealText.MS_PER_WORD)
                shown++
                if (shown % 4 == 0) onRevealProgress()
            }
            onRevealProgress()
        } else {
            shown = tokens.size
        }
        onRevealFinished()
    }

    val finished = shown >= tokens.size
    val visible = if (finished) message.text else RevealText.joined(tokens, shown)
    val bubble = BubbleMessage(
        id = message.id,
        text = visible,
        isUser = message.isUser,
        isStreaming = !finished,
        isError = message.kind == MessageKind.ERROR,
    )

    val actions: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? =
        if (!message.isUser && finished) {
            {
                if (message.kind != MessageKind.ERROR) {
                    NovaIconButton(Icons.Filled.ContentCopy, "Copy reply", onCopy)
                }
                if (message.kind != MessageKind.FORGE) {
                    NovaIconButton(Icons.Filled.Refresh, "Try again", onRetry)
                }
                if (message.kind != MessageKind.ERROR) {
                    if (isSpeaking) {
                        NovaIconButton(Icons.Filled.Stop, "Stop reading aloud", onSpeak)
                    } else {
                        NovaIconButton(Icons.AutoMirrored.Filled.VolumeUp, "Read aloud", onSpeak)
                    }
                }
            }
        } else {
            null
        }

    Column(
        modifier = modifier.graphicsLayer {
            translationY = (1f - settle.value) * 16.dp.toPx()
            alpha = 0.35f + 0.65f * settle.value
        },
    ) {
        // The shadow starts deep and settles to 4 dp as the bubble lands.
        NovaMessageBubble(
            message = bubble,
            depth = 4.dp + 6.dp * (1f - settle.value),
            actions = actions,
        )
        if (message.kind == MessageKind.FORGE && onOpenForge != null && finished) {
            ForgeChip(onClick = onOpenForge, modifier = Modifier.padding(top = NovaSpacing.sm))
        }
    }
}

/** A small glass chip under a Forge reply. Opens the Forge. */
@Composable
private fun ForgeChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NovaTheme.colors
    val shape = NovaShapes.pill
    Text(
        text = "Open Forge preview",
        style = NovaTheme.type.label,
        color = colors.accent,
        modifier = modifier
            .heightIn(min = 48.dp)
            .depthSurface(shape, 6.dp)
            .clip(shape)
            .background(colors.surfaceGlass)
            .border(1.dp, colors.accent.copy(alpha = 0.5f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = NovaSpacing.lg, vertical = NovaSpacing.md),
    )
}
