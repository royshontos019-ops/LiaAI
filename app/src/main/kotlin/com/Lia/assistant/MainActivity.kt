package com.Lia.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.Lia.assistant.data.ApiKeyStore
import com.Lia.assistant.data.AssistantBrand
import com.Lia.assistant.data.NovaAppState
import com.Lia.assistant.data.rememberNovaAppState
import com.Lia.assistant.ui.components.NovaButton
import com.Lia.assistant.ui.components.NovaGlassCard
import com.Lia.assistant.ui.components.NovaOrb
import com.Lia.assistant.ui.components.NovaSectionHeader
import com.Lia.assistant.ui.components.NovaTextField
import com.Lia.assistant.ui.screens.chat.ChatScreen
import com.Lia.assistant.ui.theme.LocalBottomBarInset
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.ui.theme.nightSky
import com.Lia.assistant.voice.NovaOrbState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appState = rememberNovaAppState()
            NovaTheme(mode = appState.themeMode, reducedMotion = appState.reducedMotion) {
                LiaRoot(appState)
            }
        }
    }
}

@Composable
private fun LiaRoot(appState: NovaAppState) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") { HomeScreen(appState, onOpenChat = { nav.navigate("chat") }) }
        composable("chat") { ChatScreen(appState = appState, onBack = { nav.popBackStack() }) }
        flavorDestinations(nav) // same function in direct and play
    }
}

@Composable
private fun HomeScreen(appState: NovaAppState, onOpenChat: () -> Unit) {
    val colors = NovaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .nightSky(tilt = true)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NovaSpacing.xl, vertical = NovaSpacing.xxl)
            .padding(bottom = LocalBottomBarInset.current),
        verticalArrangement = Arrangement.spacedBy(NovaSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(NovaSpacing.xl))
        NovaOrb(state = NovaOrbState.IDLE, diameter = 96.dp)
        Text(AssistantBrand.FULL_NAME, style = NovaTheme.type.display, color = colors.textPrimary)
        Text(AssistantBrand.TAGLINE, style = NovaTheme.type.voice, color = colors.textSecondary)

        NovaGlassCard(Modifier.fillMaxWidth()) {
            Text(
                "Hi, I'm ${appState.assistantName} (${appState.personality.displayName})",
                style = NovaTheme.type.title,
                color = colors.textPrimary,
            )
            Text(
                "Flavor: ${FlavorRoutes.FLAVOR_NAME}",
                style = NovaTheme.type.caption,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(NovaSpacing.md))
            NovaButton(
                text = "Open chat",
                onClick = onOpenChat,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.AutoMirrored.Filled.Chat,
            )
        }

        ApiKeyCard()
        AccessibilitySettingsRows()
    }
}

/**
 * TEMPORARY place to paste the Gemini API key until the Settings screen exists.
 * The key is hidden while typing, stored on the phone only, and never logged.
 */
@Composable
private fun ApiKeyCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasKey by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { hasKey = withContext(Dispatchers.IO) { ApiKeyStore.hasKey(context) } }

    Column(Modifier.fillMaxWidth()) {
        NovaSectionHeader("Gemini API key")
        NovaGlassCard(Modifier.fillMaxWidth()) {
            Text(
                if (hasKey) "Saved ✓" else "Not set yet",
                style = NovaTheme.type.body,
                color = if (hasKey) NovaTheme.colors.success else NovaTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(NovaSpacing.md))
            NovaTextField(
                value = field,
                onValueChange = { field = it },
                label = "Paste your Gemini API key",
                isPassword = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Spacer(Modifier.height(NovaSpacing.md))
            NovaButton(
                text = "Save key",
                enabled = field.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val key = field
                    field = ""
                    scope.launch {
                        withContext(Dispatchers.IO) { ApiKeyStore.saveKey(context, key) }
                        hasKey = withContext(Dispatchers.IO) { ApiKeyStore.hasKey(context) }
                    }
                },
            )
        }
    }
}
