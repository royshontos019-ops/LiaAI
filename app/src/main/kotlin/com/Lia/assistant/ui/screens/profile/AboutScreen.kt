package com.Lia.assistant.ui.screens.profile

import android.content.Context
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.Lia.assistant.FlavorRoutes
import com.Lia.assistant.data.AssistantBrand
import com.Lia.assistant.data.NovaAppState
import com.Lia.assistant.ui.components.NovaGlassCard
import com.Lia.assistant.ui.components.NovaTopBar
import com.Lia.assistant.ui.fx.LiaOrb3D
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.ui.theme.nightSky
import com.Lia.assistant.voice.NovaOrbState

/** Quiet screen with a 120 dp orb, the name, and the version. */
@Composable
fun AboutScreen(
    appState: NovaAppState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NovaTheme.colors
    val context = LocalContext.current
    val version = remember { versionName(context) }

    Box(modifier.fillMaxSize().nightSky()) {
        Column(Modifier.fillMaxSize()) {
            NovaTopBar(title = "About", onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = NovaSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(NovaSpacing.xl))
                LiaOrb3D(state = NovaOrbState.IDLE, size = 120.dp, name = appState.assistantName)
                Spacer(Modifier.height(NovaSpacing.lg))
                Text(
                    text = AssistantBrand.FULL_NAME,
                    style = NovaTheme.type.headline,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = AssistantBrand.TAGLINE,
                    style = NovaTheme.type.voice,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(NovaSpacing.xl))
                NovaGlassCard(Modifier.fillMaxWidth(), elevation = 8.dp) {
                    InfoLine("Version", version.ifBlank { "—" })
                    Spacer(Modifier.height(NovaSpacing.sm))
                    InfoLine("Build", FlavorRoutes.FLAVOR_NAME)
                }
                Spacer(Modifier.height(NovaSpacing.xl))
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val colors = NovaTheme.colors
    Column {
        Text(label, style = NovaTheme.type.caption, color = colors.textSecondary)
        Text(value, style = NovaTheme.type.body, color = colors.textPrimary)
    }
}

@Suppress("DEPRECATION")
private fun versionName(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
} catch (e: Exception) {
    ""
}
