package com.Lia.assistant.ui.screens.chat

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
) {
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

    NovaMessageBubble(message = bubble, modifier = modifier, actions = actions)
}
