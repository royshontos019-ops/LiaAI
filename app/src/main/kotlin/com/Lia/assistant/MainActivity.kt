package com.Lia.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.Lia.assistant.data.NovaAppState
import com.Lia.assistant.data.rememberNovaAppState
import com.Lia.assistant.ui.components.NovaFloatingBar
import com.Lia.assistant.ui.components.NovaFloatingBarInset
import com.Lia.assistant.ui.components.NovaTab
import com.Lia.assistant.ui.fx.EdgeGlowBus
import com.Lia.assistant.ui.fx.EdgeGlowDialogHost
import com.Lia.assistant.ui.fx.LocalTilt
import com.Lia.assistant.ui.fx.rememberDeviceTilt
import com.Lia.assistant.ui.screens.chat.ChatScreen
import com.Lia.assistant.ui.screens.home.HomeScreen
import com.Lia.assistant.ui.screens.history.HistoryScreen
import com.Lia.assistant.ui.screens.permissions.PermissionsScreen
import com.Lia.assistant.ui.screens.profile.AboutScreen
import com.Lia.assistant.ui.screens.profile.PrivacyScreen
import com.Lia.assistant.ui.screens.profile.ProfileScreen
import com.Lia.assistant.ui.screens.quickactions.QuickActionsScreen
import com.Lia.assistant.ui.screens.settings.OrbStyleScreen
import com.Lia.assistant.ui.screens.settings.PersonalityScreen
import com.Lia.assistant.ui.screens.settings.SettingsScreen
import com.Lia.assistant.ui.screens.splash.LiaSplash3D
import com.Lia.assistant.ui.screens.splash.SplashSession
import com.Lia.assistant.ui.screens.voice.VoiceScreen
import com.Lia.assistant.ui.theme.LocalBottomBarInset
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.voice.VoiceSessionManager
import androidx.compose.ui.unit.dp

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
    // Which saved chat to open, and a phrase to send at once (from Quick actions).
    var openConversationId by remember { mutableStateOf<String?>(null) }
    var autoSendText by remember { mutableStateOf<String?>(null) }
    fun openChat(conversationId: String?, autoSend: String? = null) {
        openConversationId = conversationId
        autoSendText = autoSend
        nav.navigate("chat") {
            popUpTo("home") { inclusive = false }
            launchSingleTop = true
        }
    }
    val tilt = rememberDeviceTilt(enabled = !appState.reducedMotion)
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    // The bar floats on Home and Chat; Voice is full-bleed, and the keyboard needs the room.
    val barVisible = (route == "home" || route == "chat" || route == "history" || route == "settings") && !imeVisible
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
                        onType = { openChat(null) },
                        onProfile = { nav.navigate("profile") { launchSingleTop = true } },
                        onQuickActions = { nav.navigate("quickactions") { launchSingleTop = true } },
                    )
                }
                composable("chat") {
                    ChatScreen(
                        appState = appState,
                        onBack = { nav.popBackStack() },
                        conversationId = openConversationId,
                        autoSend = autoSendText,
                        onHistory = { nav.navigate("history") { launchSingleTop = true } },
                    )
                }
                composable("voice") {
                    VoiceScreen(
                        appState = appState,
                        onClose = { nav.popBackStack() },
                        onOpenSettings = { nav.navigate("settings") { launchSingleTop = true } },
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        appState = appState,
                        onBack = { nav.popBackStack() },
                        onOpenProfile = { nav.navigate("profile") { launchSingleTop = true } },
                        onOpenPersonality = { nav.navigate("personality") { launchSingleTop = true } },
                        onOpenOrbStyle = { nav.navigate("orbstyle") { launchSingleTop = true } },
                        onOpenQuickActions = { nav.navigate("quickactions") { launchSingleTop = true } },
                        onOpenHistory = { nav.navigate("history") { launchSingleTop = true } },
                        onOpenPermissions = { nav.navigate("permissions") { launchSingleTop = true } },
                        onOpenPrivacy = { nav.navigate("privacy") { launchSingleTop = true } },
                        onOpenAbout = { nav.navigate("about") { launchSingleTop = true } },
                    ) {
                        AccessibilitySettingsRows()
                    }
                }
                composable("personality") {
                    PersonalityScreen(appState = appState, onBack = { nav.popBackStack() })
                }
                composable("orbstyle") {
                    OrbStyleScreen(appState = appState, onBack = { nav.popBackStack() })
                }
                composable("history") {
                    HistoryScreen(
                        appState = appState,
                        onBack = { nav.popBackStack() },
                        onOpen = { id -> openChat(id) },
                        onNewChat = { openChat(null) },
                    )
                }
                composable("quickactions") {
                    QuickActionsScreen(
                        appState = appState,
                        onBack = { nav.popBackStack() },
                        onVoice = { nav.navigate("voice") { launchSingleTop = true } },
                        onChat = { phrase -> openChat(null, phrase) },
                    )
                }
                composable("permissions") {
                    PermissionsScreen(onBack = { nav.popBackStack() }) { refreshKey ->
                        AccessibilityPermissionRow(refreshKey)
                    }
                }
                composable("profile") {
                    ProfileScreen(appState = appState, onBack = { nav.popBackStack() })
                }
                composable("privacy") {
                    PrivacyScreen(onBack = { nav.popBackStack() })
                }
                composable("about") {
                    AboutScreen(appState = appState, onBack = { nav.popBackStack() })
                }
                flavorDestinations(nav) // same function in direct and play
            }
            if (barVisible) {
                NovaFloatingBar(
                    selected = when (route) {
                        "chat" -> NovaTab.CHAT
                        "history" -> NovaTab.HISTORY
                        "settings" -> NovaTab.SETTINGS
                        else -> NovaTab.HOME
                    },
                    orbState = voiceState,
                    amplitude = amplitude,
                    onHome = {
                        nav.navigate("home") {
                            popUpTo("home") { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onChat = { if (route != "chat") openChat(null) },
                    onHistory = {
                        if (route != "history") {
                            nav.navigate("history") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    },
                    onSettings = {
                        if (route != "settings") {
                            nav.navigate("settings") {
                                popUpTo("home") { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    },
                    onTalk = { nav.navigate("voice") { launchSingleTop = true } },
                    modifier = Modifier.align(Alignment.BottomCenter),
                    assistantName = appState.assistantName,
                )
            }
        }
    }
}
