package com.Lia.assistant.ui.screens.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val UserShape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
private val AssistantShape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)

/**
 * One chat bubble. Assistant bubbles show copy / retry / speak. When [reveal] is true the text
 * appears word by word (20 ms per word) and [onRevealFinished] is called at the end.
 */
@Composable
fun NovaMessageBubble(
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

    val visible = if (shown >= tokens.size) message.text else RevealText.joined(tokens, shown)
    val finished = shown >= tokens.size

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start) {
            Surface(
                shape = if (message.isUser) UserShape else AssistantShape,
                color = when {
                    message.isUser -> MaterialTheme.colorScheme.primaryContainer
                    message.kind == MessageKind.ERROR -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = when {
                    message.isUser -> MaterialTheme.colorScheme.onPrimaryContainer
                    message.kind == MessageKind.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Text(
                    text = visible,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            if (!message.isUser && finished) {
                Row(modifier = Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (message.kind != MessageKind.ERROR) {
                        SmallAction(onCopy) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy") }
                    }
                    if (message.kind != MessageKind.FORGE) {
                        SmallAction(onRetry) { Icon(Icons.Filled.Refresh, contentDescription = "Try again") }
                    }
                    if (message.kind != MessageKind.ERROR) {
                        SmallAction(onSpeak) {
                            if (isSpeaking) {
                                Icon(Icons.Filled.Stop, contentDescription = "Stop speaking")
                            } else {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Read aloud")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallAction(onClick: () -> Unit, icon: @Composable () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { icon() }
    }
}

/** Three softly pulsing dots shown while Gemini is answering. */
@Composable
fun TypingBubble(reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "typingAlpha",
    )
    Surface(
        shape = AssistantShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Text(
            text = "• • •",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .alpha(if (reducedMotion) 0.7f else pulse),
        )
    }
}
