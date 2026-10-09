package com.Lia.assistant.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.Lia.assistant.data.AssistantBrand
import com.Lia.assistant.data.LanguagePreference
import com.Lia.assistant.data.NovaAppState
import com.Lia.assistant.data.ThemeMode
import com.Lia.assistant.ui.components.NovaChoiceChips
import com.Lia.assistant.ui.components.NovaGlassCard
import com.Lia.assistant.ui.components.NovaSectionHeader
import com.Lia.assistant.ui.components.NovaSettingsRow
import com.Lia.assistant.ui.components.NovaTextField
import com.Lia.assistant.ui.components.NovaTopBar
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.ui.theme.nightSky
import kotlinx.coroutines.delay

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
    onOpenPersonality: () -> Unit,
    onOpenOrbStyle: () -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = NovaTheme.colors

    // The two name fields save a moment after you stop typing.
    var nameField by remember(appState.isLoaded) { mutableStateOf(appState.userName) }
    var assistantField by remember(appState.isLoaded) { mutableStateOf(appState.assistantName) }
    LaunchedEffect(nameField) {
        delay(500)
        if (nameField.trim() != appState.userName) appState.applyUserName(nameField)
    }
    LaunchedEffect(assistantField) {
        delay(700)
        val clean = assistantField.trim().ifEmpty { AssistantBrand.NAME }
        if (clean != appState.assistantName) appState.applyAssistantName(assistantField)
    }

    Box(modifier.fillMaxSize().nightSky(tilt = true)) {
        Column(Modifier.fillMaxSize()) {
            NovaTopBar(title = "Settings", onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = NovaSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(NovaSpacing.lg),
            ) {
                // 0: profile
                NovaGlassCard(Modifier.fillMaxWidth(), elevation = depthOf(0)) {
                    Box(
                        modifier = Modifier.align(Alignment.CenterHorizontally).size(104.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(colors.accentGlow, Color.Transparent),
                                            center = center,
                                            radius = size.minDimension / 2f,
                                        ),
                                        radius = size.minDimension / 2f,
                                    )
                                },
                        )
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceRaised)
                                .border(1.dp, colors.accent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = nameField.trim().take(1).uppercase().ifEmpty { "✦" },
                                style = NovaTheme.type.headline,
                                color = colors.accent,
                            )
                        }
                    }
                    Spacer(Modifier.height(NovaSpacing.md))
                    NovaTextField(
                        value = nameField,
                        onValueChange = { nameField = it },
                        label = "Your name",
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                    Spacer(Modifier.height(NovaSpacing.md))
                    NovaTextField(
                        value = assistantField,
                        onValueChange = { assistantField = it },
                        label = "Assistant name",
                        supportingText = "Empty means ${AssistantBrand.NAME}",
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
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

                // 3: the Gemini API key (moved here from Home)
                ApiKeyCard(elevation = depthOf(3))

                // Screen control (direct build only; empty in the Play build)
                extra()

                Text(
                    text = "${AssistantBrand.FULL_NAME} · ${AssistantBrand.TAGLINE}",
                    style = NovaTheme.type.caption,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(NovaSpacing.xl))
            }
        }
    }
}
