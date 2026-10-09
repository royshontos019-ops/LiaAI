package com.Lia.assistant.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.Lia.assistant.data.AssistantBrand
import com.Lia.assistant.data.LanguagePreference
import com.Lia.assistant.data.NovaAppState
import com.Lia.assistant.data.ThemeMode
import com.Lia.assistant.ui.components.NovaAvatar
import com.Lia.assistant.ui.components.NovaChoiceChips
import com.Lia.assistant.ui.components.NovaGlassCard
import com.Lia.assistant.ui.components.NovaSectionHeader
import com.Lia.assistant.ui.components.NovaSettingsRow
import com.Lia.assistant.ui.components.NovaTopBar
import com.Lia.assistant.ui.theme.LocalBottomBarInset
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.ui.theme.nightSky

private val VoiceNames = listOf("Aoede", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Zephyr")

/** Each card sits 2 dp deeper than the one above it. */
private fun depthOf(index: Int) = (6 + 2 * index).dp

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.DARK -> "Dark"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.SYSTEM -> "System"
}

/**
 * Settings: stacked glass cards at slightly different depths. The profile card is on top.
 * [extra] is a slot for flavour-specific rows (screen control) below the API key.
 */
@Composable
fun SettingsScreen(
    appState: NovaAppState,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenPersonality: () -> Unit,
    onOpenOrbStyle: () -> Unit,
    onOpenQuickActions: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = NovaTheme.colors

    Box(modifier.fillMaxSize().nightSky(tilt = true)) {
        Column(Modifier.fillMaxSize()) {
            NovaTopBar(title = "Settings", onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = NovaSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(NovaSpacing.lg),
            ) {
                // 0: profile
                NovaGlassCard(Modifier.fillMaxWidth(), elevation = depthOf(0)) {
                    NovaAvatar(
                        initial = appState.userName.trim().take(1).uppercase().ifEmpty { "✦" },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(NovaSpacing.sm))
                    Text(
                        text = appState.userName.ifBlank { "Add your name" },
                        style = NovaTheme.type.title,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Your assistant: ${appState.assistantName}",
                        style = NovaTheme.type.caption,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(NovaSpacing.sm))
                    NovaSettingsRow(
                        title = "Edit profile",
                        icon = Icons.Filled.Person,
                        onClick = onOpenProfile,
                    )
                }

                // 1: appearance
                Column(Modifier.fillMaxWidth()) {
                    NovaSectionHeader("Appearance")
                    NovaGlassCard(Modifier.fillMaxWidth(), elevation = depthOf(1)) {
                        Text("Theme", style = NovaTheme.type.label, color = colors.textSecondary)
                        Spacer(Modifier.height(NovaSpacing.sm))
                        NovaChoiceChips(
                            options = listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM),
                            selected = appState.themeMode,
                            label = { themeLabel(it) },
                            onSelect = { appState.applyThemeMode(it) },
                        )
                        Spacer(Modifier.height(NovaSpacing.md))
                        NovaSettingsRow(
                            title = "Reduce motion",
                            subtitle = "Calmer screens, no tilt effects",
                            switchChecked = appState.reducedMotion,
                            onSwitchChange = { appState.applyReducedMotion(it) },
                        )
                    }
                }

                // 2: assistant
                Column(Modifier.fillMaxWidth()) {
                    NovaSectionHeader("Assistant")
                    NovaGlassCard(Modifier.fillMaxWidth(), elevation = depthOf(2)) {
                        NovaSettingsRow(
                            title = "Personality",
                            icon = Icons.Filled.Face,
                            value = appState.personality.displayName,
                            onClick = onOpenPersonality,
                        )
                        NovaSettingsRow(
                            title = "Orb style",
                            icon = Icons.Filled.Star,
                            value = orbStyleLabel(orbStyleFromId(appState.orbStyle)),
                            onClick = onOpenOrbStyle,
                        )
                        Spacer(Modifier.height(NovaSpacing.md))
                        Text("Language", style = NovaTheme.type.label, color = colors.textSecondary)
                        Spacer(Modifier.height(NovaSpacing.sm))
                        NovaChoiceChips(
                            options = LanguagePreference.entries.toList(),
                            selected = appState.language,
                            label = { it.displayName },
                            onSelect = { appState.applyLanguage(it) },
                            columns = 2,
                        )
                        Spacer(Modifier.height(NovaSpacing.xs))
                        Text(
                            text = appState.language.description,
                            style = NovaTheme.type.caption,
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.height(NovaSpacing.md))
                        Text("Voice", style = NovaTheme.type.label, color = colors.textSecondary)
                        Spacer(Modifier.height(NovaSpacing.sm))
                        NovaChoiceChips(
                            options = VoiceNames,
                            selected = VoiceNames.firstOrNull { it.equals(appState.voiceName, ignoreCase = true) },
                            label = { it },
                            onSelect = { appState.applyVoiceName(it) },
                            columns = 4,
                        )
                    }
                }

                // 3: more screens
                Column(Modifier.fillMaxWidth()) {
                    NovaSectionHeader("More")
                    NovaGlassCard(Modifier.fillMaxWidth(), elevation = depthOf(3)) {
                        NovaSettingsRow(title = "Quick actions", icon = Icons.Filled.Build, onClick = onOpenQuickActions)
                        NovaSettingsRow(title = "History", icon = Icons.Filled.History, onClick = onOpenHistory)
                        NovaSettingsRow(title = "Permissions", icon = Icons.Filled.CheckCircle, onClick = onOpenPermissions)
                        NovaSettingsRow(title = "Privacy", icon = Icons.Filled.Lock, onClick = onOpenPrivacy)
                        NovaSettingsRow(title = "About", icon = Icons.Filled.Info, onClick = onOpenAbout)
                    }
                }

                // 4: the Gemini API key
                ApiKeyCard(elevation = depthOf(4))

                // Screen control (direct build only; empty in the Play build)
                extra()

                Text(
                    text = "${AssistantBrand.FULL_NAME} · ${AssistantBrand.TAGLINE}",
                    style = NovaTheme.type.caption,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(NovaSpacing.xl + LocalBottomBarInset.current))
            }
        }
    }
}
