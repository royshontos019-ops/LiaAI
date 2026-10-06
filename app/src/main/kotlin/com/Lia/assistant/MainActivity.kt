package com.Lia.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.Lia.assistant.data.AssistantBrand
import com.Lia.assistant.data.NovaAppState
import com.Lia.assistant.data.ThemeMode
import com.Lia.assistant.data.rememberNovaAppState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appState = rememberNovaAppState()
            val dark = when (appState.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            LiaTheme(darkTheme = dark) { LiaRoot(appState) }
        }
    }
}

@Composable
private fun LiaRoot(appState: NovaAppState) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") { HomeScreen(appState) }
        flavorDestinations(nav) // same function in direct and play
    }
}

@Composable
private fun HomeScreen(appState: NovaAppState) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(AssistantBrand.FULL_NAME, style = MaterialTheme.typography.headlineMedium)
            Text(AssistantBrand.TAGLINE, style = MaterialTheme.typography.bodyLarge)
            Text("Hi, I'm ${appState.assistantName} (${appState.personality.displayName})")
            Text("Flavor: ${FlavorRoutes.FLAVOR_NAME}", style = MaterialTheme.typography.bodyMedium)
            AccessibilitySettingsRows()
        }
    }
}
