package com.Lia.assistant.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.Lia.assistant.FlavorRoutes
import com.Lia.assistant.data.AssistantBrand
import com.Lia.assistant.data.ConversationStore
import com.Lia.assistant.ui.components.NovaButton
import com.Lia.assistant.ui.components.NovaButtonStyle
import com.Lia.assistant.ui.components.NovaConfirmDialog
import com.Lia.assistant.ui.components.NovaGlassCard
import com.Lia.assistant.ui.components.NovaSectionHeader
import com.Lia.assistant.ui.components.NovaSettingsRow
import com.Lia.assistant.ui.components.NovaTopBar
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.ui.theme.nightSky
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Quiet screen: what is kept on the phone, what leaves it, and how to wipe the history. */
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NovaTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var saving by remember { mutableStateOf(ConversationStore.isSavingEnabled(context)) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize().nightSky()) {
        Column(Modifier.fillMaxSize()) {
            NovaTopBar(title = "Privacy", onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = NovaSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(NovaSpacing.lg),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    NovaSectionHeader("Your history")
                    NovaGlassCard(Modifier.fillMaxWidth(), elevation = 8.dp) {
                        NovaSettingsRow(
                            title = "Save conversations",
                            subtitle = "Keep your chats on this phone",
                            switchChecked = saving,
                            onSwitchChange = {
                                saving = it
                                ConversationStore.setSavingEnabled(context, it)
                            },
                        )
                        Spacer(Modifier.height(NovaSpacing.md))
                        NovaButton(
                            text = "Delete all history",
                            onClick = { confirmDelete = true },
                            style = NovaButtonStyle.SECONDARY,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (deleted) {
                            Spacer(Modifier.height(NovaSpacing.sm))
                            Text("History deleted ✓", style = NovaTheme.type.caption, color = colors.success)
                        }
                    }
                }

                Column(Modifier.fillMaxWidth()) {
                    NovaSectionHeader("What stays, what leaves")
                    NovaGlassCard(Modifier.fillMaxWidth(), elevation = 10.dp) {
                        PrivacyPoint(
                            "On this phone",
                            "Your saved chats, your settings and your Gemini API key are stored on this phone.",
                        )
                        Spacer(Modifier.height(NovaSpacing.md))
                        PrivacyPoint(
                            "Sent to Gemini",
                            "To answer you, ${AssistantBrand.NAME} sends what you type or say to Google's Gemini service, using your own API key.",
                        )
                        if (FlavorRoutes.SUPPORTS_SCREEN_CONTROL) {
                            Spacer(Modifier.height(NovaSpacing.md))
                            PrivacyPoint(
                                "Screen control",
                                "It only works after you switch it on in Android's accessibility settings, and you can switch it off there at any time.",
                            )
                        }
                    }
                }
                Spacer(Modifier.height(NovaSpacing.xl))
            }
        }
    }

    if (confirmDelete) {
        NovaConfirmDialog(
            title = "Delete all history?",
            message = "Every saved conversation will be removed from this phone. This can't be undone.",
            confirmText = "Delete",
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    withContext(Dispatchers.IO) { ConversationStore.deleteAll(context) }
                    deleted = true
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun PrivacyPoint(title: String, text: String) {
    val colors = NovaTheme.colors
    Text(title, style = NovaTheme.type.title, color = colors.textPrimary)
    Spacer(Modifier.height(NovaSpacing.xs))
    Text(text, style = NovaTheme.type.body, color = colors.textSecondary)
}
