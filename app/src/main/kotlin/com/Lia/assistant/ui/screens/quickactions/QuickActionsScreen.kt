package com.Lia.assistant.ui.screens.quickactions

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.Lia.assistant.FlavorRoutes
import com.Lia.assistant.data.NovaAppState
import com.Lia.assistant.ui.components.NovaGlassCard
import com.Lia.assistant.ui.components.NovaTopBar
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.ui.theme.nightSky

private data class QuickAction(
    val title: String,
    val phrase: String,
    val icon: ImageVector,
    /** True: opens the chat and sends the phrase. False: opens Voice, where you say it. */
    val viaChat: Boolean,
)

/** Only things Lia can really do. Screen control tiles exist only in the direct build. */
private fun quickActions(): List<QuickAction> = buildList {
    add(QuickAction("Open an app", "YouTube kholo", Icons.Filled.PlayArrow, viaChat = false))
    add(QuickAction("Call someone", "Rahim ke call koro", Icons.Filled.Call, viaChat = false))
    add(QuickAction("Send a message", "Mayer kache message pathao", Icons.Filled.Email, viaChat = false))
    if (FlavorRoutes.SUPPORTS_SCREEN_CONTROL) {
        add(QuickAction("Read my screen", "Read my screen", Icons.Filled.Search, viaChat = false))
        add(QuickAction("Tap a button", "Tap Search", Icons.Filled.Check, viaChat = false))
        add(QuickAction("Go home", "Go home", Icons.Filled.Home, viaChat = false))
    }
    add(QuickAction("Build a website", "Build a website", Icons.Filled.Build, viaChat = true))
}

/**
 * A 2-column grid of tiles. Voice tiles open Voice (say the example out loud); the website tile
 * opens the chat and asks for it. Pressing a tile pushes it down (smaller, softer shadow).
 */
@Composable
fun QuickActionsScreen(
    appState: NovaAppState,
    onBack: () -> Unit,
    onVoice: () -> Unit,
    onChat: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NovaTheme.colors
    val actions = remember { quickActions() }

    Box(modifier.fillMaxSize().nightSky(tilt = true)) {
        Column(Modifier.fillMaxSize()) {
            NovaTopBar(title = "Quick actions", onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = NovaSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(NovaSpacing.md),
            ) {
                Text(
                    text = "Tap one, then say the example",
                    style = NovaTheme.type.voice,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = NovaSpacing.xs),
                )
                actions.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(NovaSpacing.md),
                    ) {
                        pair.forEach { action ->
                            QuickTile(
                                action = action,
                                reduced = appState.reducedMotion,
                                onClick = { if (action.viaChat) onChat(action.phrase) else onVoice() },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                        if (pair.size < 2) Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(NovaSpacing.xl))
            }
        }
    }
}

@Composable
private fun QuickTile(
    action: QuickAction,
    reduced: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NovaTheme.colors
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(if (pressed && !reduced) 0.96f else 1f, label = "tileScale")
    val elevation by animateDpAsState(if (pressed) 3.dp else 8.dp, label = "tileElevation")

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        NovaGlassCard(
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            elevation = elevation,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(action.icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(NovaSpacing.sm))
            Text(action.title, style = NovaTheme.type.title, color = colors.textPrimary)
            Text(
                text = "“${action.phrase}”",
                style = NovaTheme.type.caption.copy(fontStyle = FontStyle.Italic),
                color = colors.textSecondary,
            )
        }
    }
}
