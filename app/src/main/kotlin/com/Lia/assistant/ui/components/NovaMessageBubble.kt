package com.Lia.assistant.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.ui.theme.depthSurface

/** What the bubble needs to know. [isStreaming] shows a soft caret while the text is still arriving. */
data class ChatMessage(
    val id: Long,
    val text: String,
    val isUser: Boolean,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
)

private val UserShape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
private val AssistantShape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)

/**
 * One chat bubble. Your messages sit on the right, the assistant's on the left, errors are tinted.
 * [actions] (copy, retry, ...) appear under the bubble when given.
 */
@Composable
fun NovaMessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    depth: Dp = 4.dp,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = NovaTheme.colors
    val shape = if (message.isUser) UserShape else AssistantShape
    val background = when {
        message.isError -> colors.error.copy(alpha = 0.16f)
        message.isUser -> colors.userBubble
        else -> colors.assistantBubble
    }
    val borderColor = when {
        message.isError -> colors.error.copy(alpha = 0.6f)
        message.isUser -> colors.accent.copy(alpha = 0.35f)
        else -> colors.surfaceBorder
    }

    val caretAlpha = if (message.isStreaming && !NovaTheme.reducedMotion) {
        val transition = rememberInfiniteTransition(label = "caret")
        val alpha by transition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
            label = "caretAlpha",
        )
        alpha
    } else {
        1f
    }
    val caretColor = colors.accent
    val textColor = colors.textPrimary
    val shown = buildAnnotatedString {
        append(message.text)
        if (message.isStreaming) {
            withStyle(SpanStyle(color = caretColor.copy(alpha = caretAlpha))) { append(" ▍") }
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start) {
            Text(
                text = shown,
                style = NovaTheme.type.body,
                color = textColor,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .depthSurface(shape, depth)
                    .clip(shape)
                    .background(background)
                    .border(1.dp, borderColor, shape)
                    .padding(horizontal = 14.dp, vertical = NovaSpacing.md)
                    .semantics { if (message.isError) contentDescription = "Error: ${message.text}" },
            )
            if (actions != null) {
                Row(
                    modifier = Modifier.padding(start = NovaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}

/** The assistant's "typing" bubble: three pulsing dots inside a bubble. */
@Composable
fun NovaTypingBubble(modifier: Modifier = Modifier) {
    val colors = NovaTheme.colors
    NovaTypingDots(
        modifier = modifier
            .depthSurface(AssistantShape, 4.dp)
            .clip(AssistantShape)
            .background(colors.assistantBubble)
            .border(1.dp, colors.surfaceBorder, AssistantShape)
            .padding(horizontal = NovaSpacing.lg, vertical = 14.dp),
    )
}
