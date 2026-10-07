package com.Lia.assistant.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Lia.assistant.data.NovaAppState
import com.Lia.assistant.ui.components.NovaOrb
import com.Lia.assistant.voice.NovaOrbState
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    appState: NovaAppState,
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(),
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val typing by viewModel.isTyping.collectAsStateWithLifecycle()

    // Created once; the speech engine is released when the screen leaves.
    val speech = remember { SpeechController(context) }
    DisposableEffect(speech) { onDispose { speech.shutdown() } }
    var speakingId by remember { mutableStateOf<Long?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }

    val itemCount = messages.size + if (typing) 1 else 0
    val latestCount by rememberUpdatedState(itemCount)

    // Follow new messages, and the keyboard opening.
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(itemCount, imeBottom) {
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
            listState.scrollBy(100_000f) // a tall last message: show its end, not its start
        }
    }

    val orbState = when {
        typing -> NovaOrbState.THINKING
        speech.speaking -> NovaOrbState.SPEAKING
        else -> NovaOrbState.IDLE
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        ChatHeader(
            name = appState.assistantName,
            typing = typing,
            orbState = orbState,
            reducedMotion = appState.reducedMotion,
            onBack = onBack,
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty() && !typing) {
                Text(
                    text = "Say hi to ${appState.assistantName}.\nAsk anything, or ask her to build a website.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    NovaMessageBubble(
                        message = message,
                        reveal = !appState.reducedMotion,
                        isSpeaking = speech.speaking && speakingId == message.id,
                        onCopy = { copyToClipboard(context, message.text) },
                        onRetry = { viewModel.retry(message.id) },
                        onSpeak = {
                            if (speech.speaking && speakingId == message.id) {
                                speech.stop()
                            } else {
                                speakingId = message.id
                                if (!speech.speak(message.text)) {
                                    Toast.makeText(context, "Speech isn't ready yet", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onRevealProgress = { scope.launch { if (latestCount > 0) listState.scrollBy(100_000f) } },
                        onRevealFinished = { viewModel.finishReveal(message.id) },
                    )
                }
                if (typing) {
                    item(key = "typing") { TypingBubble(reducedMotion = appState.reducedMotion) }
                }
            }
        }

        GlassInputBar(
            value = input,
            onValueChange = { input = it },
            onSend = {
                if (input.isNotBlank() && !typing) {
                    viewModel.send(input)
                    input = ""
                }
            },
            canSend = input.isNotBlank() && !typing,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun ChatHeader(
    name: String,
    typing: Boolean,
    orbState: NovaOrbState,
    reducedMotion: Boolean,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        NovaOrb(state = orbState, diameter = 38.dp, reducedMotion = reducedMotion)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (typing) "Typing…" else "Here to help",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Rounded, translucent bar with a light edge: the "glass" look. Sends on the keyboard's Send key. */
@Composable
private fun GlassInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.06f))),
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
            IconButton(onClick = onSend, enabled = canSend) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Lia", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
