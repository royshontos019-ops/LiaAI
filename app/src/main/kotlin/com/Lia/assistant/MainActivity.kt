package com.Lia.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.Lia.assistant.data.ApiKeyStore
import com.Lia.assistant.data.NovaAppState
import com.Lia.assistant.data.rememberNovaAppState
import com.Lia.assistant.ui.components.NovaButton
import com.Lia.assistant.ui.components.NovaFloatingBar
import com.Lia.assistant.ui.components.NovaFloatingBarInset
import com.Lia.assistant.ui.components.NovaGlassCard
import com.Lia.assistant.ui.components.NovaSectionHeader
import com.Lia.assistant.ui.components.NovaTab
import com.Lia.assistant.ui.components.NovaTextField
import com.Lia.assistant.ui.fx.EdgeGlowBus
import com.Lia.assistant.ui.fx.EdgeGlowDialogHost
import com.Lia.assistant.ui.fx.LocalTilt
import com.Lia.assistant.ui.fx.rememberDeviceTilt
import com.Lia.assistant.ui.screens.chat.ChatScreen
import com.Lia.assistant.ui.screens.home.HomeScreen
import com.Lia.assistant.ui.screens.splash.LiaSplash3D
import com.Lia.assistant.ui.screens.splash.SplashSession
import com.Lia.assistant.ui.screens.voice.VoiceScreen
import com.Lia.assistant.ui.theme.LocalBottomBarInset
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.voice.VoiceSessionManager
import androidx.compose.ui.unit.dp
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

            // The edge glow follows the live voice state.
            LaunchedEffect(Unit) {
                VoiceSessionManager.state.collect { EdgeGlowBus.setFromOrb(it) }
            }

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
    val tilt = rememberDeviceTilt(enabled = !appState.reducedMotion)
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // The bar floats on Home and Chat; Voice is full-bleed, and the keyboard needs the room.
    val barVisible = (route == "home" || route == "chat") && !imeVisible
    val voiceState by VoiceSessionManager.state.collectAsStateWithLifecycle()
    val amplitude by VoiceSessionManager.amplitude.collectAsStateWithLifecycle()

    CompositionLocalProvider(
        LocalTilt provides tilt,
        LocalBottomBarInset provides (if (barVisible) NovaFloatingBarInset else 0.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            NavHost(navController = nav, startDestination = "home") {
                composable("home") {
                    HomeScreen(
                        appState = appState,
                        onTalk = { nav.navigate("voice") { launchSingleTop = true } },
                        onType = { nav.navigate("chat") { launchSingleTop = true } },
                    ) {
                        ApiKeyCard()
                        AccessibilitySettingsRows()
                    }
                }
                composable("chat") { ChatScreen(appState = appState, onBack = { nav.popBackStack() }) }
                composable("voice") { VoiceScreen(appState = appState, onClose = { nav.popBackStack() }) }
                flavorDestinations(nav) // same function in direct and play
            }
            if (barVisible) {
                NovaFloatingBar(
                    selected = if (route == "chat") NovaTab.CHAT else NovaTab.HOME,
                    orbState = voiceState,
                    amplitude = amplitude,
                    onHome = {
                        nav.navigate("home") {
                            popUpTo("home") { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onChat = { nav.navigate("chat") { launchSingleTop = true } },
                    onTalk = { nav.navigate("voice") { launchSingleTop = true } },
                    modifier = Modifier.align(Alignment.BottomCenter),
                    assistantName = appState.assistantName,
                )
            }
        }
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
