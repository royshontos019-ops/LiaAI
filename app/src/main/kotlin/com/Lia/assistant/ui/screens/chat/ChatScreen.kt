package com.Lia.assistant.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Lia.assistant.data.NovaAppState
import com.Lia.assistant.ui.components.NovaComposerBar
import com.Lia.assistant.ui.components.NovaEmptyState
import com.Lia.assistant.ui.components.NovaTopBar
import com.Lia.assistant.ui.components.NovaTypingBubble
import com.Lia.assistant.ui.fx.LiaOrb3D
import com.Lia.assistant.ui.theme.LocalBottomBarInset
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.nightSky
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
    // Ids already shown once. A bubble only rises into place the first time it appears.
    val seen = remember { mutableSetOf<Long>() }
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
            .nightSky()
            .imePadding(),
    ) {
        NovaTopBar(
            title = appState.assistantName,
            subtitle = if (typing) "Typing…" else "Here to help",
            onBack = onBack,
            leading = { LiaOrb3D(state = orbState, size = 48.dp, name = appState.assistantName) },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty() && !typing) {
                NovaEmptyState(
                    title = "Say hi to ${appState.assistantName}",
                    message = "Ask anything, or ask her to build a website.",
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = NovaSpacing.md, vertical = NovaSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(NovaSpacing.sm),
            ) {
                items(messages, key = { it.id }) { message ->
                    val rise = remember(message.id) { seen.add(message.id) }
                    ChatMessageItem(
                        message = message,
                        rise = rise,
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
                    item(key = "typing") { NovaTypingBubble() }
                }
            }
        }

        NovaComposerBar(
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
                .padding(horizontal = NovaSpacing.md, vertical = NovaSpacing.sm)
                .navigationBarsPadding()
                .padding(bottom = LocalBottomBarInset.current),
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Lia", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
