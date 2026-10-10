package com.Lia.assistant.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.Lia.assistant.data.AssistantBrand
import com.Lia.assistant.data.NovaAppState
import com.Lia.assistant.license.AccessKeySection
import com.Lia.assistant.ui.components.NovaAvatar
import com.Lia.assistant.ui.components.NovaGlassCard
import com.Lia.assistant.ui.components.NovaTextField
import com.Lia.assistant.ui.components.NovaTopBar
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.nightSky
import kotlinx.coroutines.delay

/** Quiet screen: your name and your assistant's name. Both save a moment after you stop typing. */
@Composable
fun ProfileScreen(
    appState: NovaAppState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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

    Box(modifier.fillMaxSize().nightSky()) {
        Column(Modifier.fillMaxSize()) {
            NovaTopBar(title = "Profile", onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = NovaSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(NovaSpacing.lg),
            ) {
                Spacer(Modifier.height(NovaSpacing.md))
                NovaAvatar(
                    initial = nameField.trim().take(1).uppercase().ifEmpty { "✦" },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                NovaGlassCard(Modifier.fillMaxWidth(), elevation = 8.dp) {
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
                AccessKeySection()
                Spacer(Modifier.height(NovaSpacing.xl))
            }
        }
    }
}
