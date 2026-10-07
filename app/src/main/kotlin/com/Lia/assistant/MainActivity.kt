package com.Lia.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.Lia.assistant.data.ApiKeyStore
import com.Lia.assistant.data.AssistantBrand
import com.Lia.assistant.data.NovaAppState
import com.Lia.assistant.data.ThemeMode
import com.Lia.assistant.data.rememberNovaAppState
import com.Lia.assistant.ui.screens.chat.ChatScreen
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
        composable("home") { HomeScreen(appState, onOpenChat = { nav.navigate("chat") }) }
        composable("chat") { ChatScreen(appState = appState, onBack = { nav.popBackStack() }) }
        flavorDestinations(nav) // same function in direct and play
    }
}

@Composable
private fun HomeScreen(appState: NovaAppState, onOpenChat: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(AssistantBrand.FULL_NAME, style = MaterialTheme.typography.headlineMedium)
            Text(AssistantBrand.TAGLINE, style = MaterialTheme.typography.bodyLarge)
            Text("Hi, I'm ${appState.assistantName} (${appState.personality.displayName})")
            Text("Flavor: ${FlavorRoutes.FLAVOR_NAME}", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onOpenChat) { Text("Open chat") }
            ApiKeyBox()
            AccessibilitySettingsRows()
        }
    }
}

/**
 * TEMPORARY place to paste the Gemini API key until the Settings screen exists.
 * The key is hidden while typing, stored on the phone only, and never logged.
 */
@Composable
private fun ApiKeyBox() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasKey by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { hasKey = withContext(Dispatchers.IO) { ApiKeyStore.hasKey(context) } }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (hasKey) "Gemini API key: saved ✓" else "Gemini API key: not set yet",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = field,
            onValueChange = { field = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Paste your Gemini API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Button(
            enabled = field.isNotBlank(),
            onClick = {
                val key = field
                field = ""
                scope.launch {
                    withContext(Dispatchers.IO) { ApiKeyStore.saveKey(context, key) }
                    hasKey = withContext(Dispatchers.IO) { ApiKeyStore.hasKey(context) }
                }
            },
        ) { Text("Save key") }
    }
}
