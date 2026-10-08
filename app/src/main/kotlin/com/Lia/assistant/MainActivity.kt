package com.Lia.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import com.Lia.assistant.ui.components.NovaOrbStyle
import com.Lia.assistant.ui.components.NovaSectionHeader
import com.Lia.assistant.ui.components.NovaTextField
import com.Lia.assistant.ui.fx.EdgeGlowBus
import com.Lia.assistant.ui.fx.EdgeGlowDialogHost
import com.Lia.assistant.ui.fx.LiaOrb3D
import com.Lia.assistant.ui.screens.chat.ChatScreen
import com.Lia.assistant.ui.screens.splash.LiaSplash3D
import com.Lia.assistant.ui.screens.splash.SplashSession
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
            // The 3D intro plays once per process; rotation and resume never replay it.
            var splashVisible by remember { mutableStateOf(SplashSession.claim()) }
            NovaTheme(mode = appState.themeMode, reducedMotion = appState.reducedMotion) {
                Box(Modifier.fillMaxSize()) {
                    LiaRoot(appState)
                    if (splashVisible) LiaSplash3D(onFinished = { splashVisible = false })
                }
                EdgeGlowDialogHost() // above every screen
            }
        }
    }

    override fun onStart() {
        super.onStart()
        EdgeGlowBus.appVisible.value = true
    }

    override fun onStop() {
        EdgeGlowBus.appVisible.value = false
        super.onStop()
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
    // Preview only: tap the orb to walk through the states until the voice screen exists.
    var demo by remember { mutableStateOf(NovaOrbState.IDLE) }
    LaunchedEffect(demo) { EdgeGlowBus.setFromOrb(demo) }
    val demoLevel by rememberInfiniteTransition(label = "demoLevel").animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "demoLevelValue",
    )
    val demoAmplitude =
        if (demo == NovaOrbState.SPEAKING || demo == NovaOrbState.LISTENING) demoLevel else 0f

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
        LiaOrb3D(
            state = demo,
            size = 200.dp,
            amplitude = demoAmplitude,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { demo = NovaOrbState.entries[(demo.ordinal + 1) % NovaOrbState.entries.size] },
        )
        Text(
            "Tap the orb: ${demo.name.lowercase()}",
            style = NovaTheme.type.caption,
            color = colors.textSecondary,
        )
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

        NovaGlassCard(Modifier.fillMaxWidth()) {
            Text("Orb styles (preview)", style = NovaTheme.type.title, color = colors.textPrimary)
            Spacer(Modifier.height(NovaSpacing.md))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(NovaSpacing.lg),
            ) {
                NovaOrbStyle.entries.forEach { orbStyle ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NovaOrb(state = demo, diameter = 72.dp, amplitude = demoAmplitude, style = orbStyle)
                        Text(
                            orbStyle.name.lowercase(),
                            style = NovaTheme.type.caption,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(NovaSpacing.md))
            NovaButton(
                text = "Test edge glow",
                onClick = { EdgeGlowBus.active.value = !EdgeGlowBus.active.value },
                modifier = Modifier.fillMaxWidth(),
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
