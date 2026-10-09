package com.Lia.assistant.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.Lia.assistant.ui.fx.LiaOrb3D
import com.Lia.assistant.ui.theme.NovaMotion
import com.Lia.assistant.ui.theme.NovaShapes
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.ui.theme.depthSurface
import com.Lia.assistant.voice.NovaOrbState

enum class NovaTab { HOME, CHAT, HISTORY, SETTINGS }

/** Screens add this much bottom padding while the bar is showing, so content ends above it. */
val NovaFloatingBarInset: Dp = 88.dp

private val BarHeight = 64.dp
private val OrbSlot = 88.dp
private val OrbSize = 84.dp

/**
 * The floating navigation bar: Home and Chat on the left, History and Settings on the right, the
 * Talk orb docked in the middle and raised a little above the bar. It floats over the content and casts a soft shadow.
 */
@Composable
fun NovaFloatingBar(
    selected: NovaTab,
    orbState: NovaOrbState,
    amplitude: Float,
    onHome: () -> Unit,
    onChat: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onTalk: () -> Unit,
    modifier: Modifier = Modifier,
    assistantName: String = "Lia",
) {
    val colors = NovaTheme.colors
    val reduced = NovaTheme.reducedMotion
    val haptic = LocalHapticFeedback.current
    val shape = NovaShapes.pill

    val orbInteraction = remember { MutableInteractionSource() }
    val orbPressed by orbInteraction.collectIsPressedAsState()
    val orbScale by animateFloatAsState(
        targetValue = if (orbPressed && !reduced) 0.96f else 1f,
        animationSpec = tween(NovaMotion.FAST_MS),
        label = "dockedOrbScale",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = NovaSpacing.xl)
            .padding(bottom = NovaSpacing.sm),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .depthSurface(shape, 12.dp)
                .clip(shape)
                .background(colors.surfaceGlass)
                .border(1.dp, colors.surfaceBorder, shape),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            BarItem(
                label = "Home",
                icon = Icons.Filled.Home,
                selected = selected == NovaTab.HOME,
                onClick = onHome,
                modifier = Modifier.weight(1f),
            )
            BarItem(
                label = "Chat",
                icon = Icons.AutoMirrored.Filled.Chat,
                selected = selected == NovaTab.CHAT,
                onClick = onChat,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(OrbSlot))
            BarItem(
                label = "History",
                icon = Icons.Filled.History,
                selected = selected == NovaTab.HISTORY,
                onClick = onHistory,
                modifier = Modifier.weight(1f),
            )
            BarItem(
                label = "Settings",
                icon = Icons.Filled.Settings,
                selected = selected == NovaTab.SETTINGS,
                onClick = onSettings,
                modifier = Modifier.weight(1f),
            )
        }

        // The docked orb, a little above the bar's middle.
        Box(
            modifier = Modifier
                .padding(bottom = 6.dp)
                .size(OrbSize)
                .graphicsLayer {
                    scaleX = orbScale
                    scaleY = orbScale
                }
                .clickable(
                    interactionSource = orbInteraction,
                    indication = null,
                    onClickLabel = "Talk",
                    role = Role.Button,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTalk()
                }
                .semantics { contentDescription = "Talk to $assistantName" },
            contentAlignment = Alignment.Center,
        ) {
            LiaOrb3D(state = orbState, size = OrbSize, amplitude = amplitude, name = assistantName)
        }
    }
}

@Composable
private fun BarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NovaTheme.colors
    val tint = if (selected) colors.accent else colors.textSecondary
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = if (selected) "$label, current screen" else label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, style = NovaTheme.type.caption, color = tint)
    }
}
