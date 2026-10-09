package com.Lia.assistant.ui.screens.settings

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Lia.assistant.data.NovaAppState
import com.Lia.assistant.data.Personality
import com.Lia.assistant.ui.components.NovaGlassCard
import com.Lia.assistant.ui.components.NovaTopBar
import com.Lia.assistant.ui.theme.NovaMotion
import com.Lia.assistant.ui.theme.NovaShapes
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.ui.theme.nightSky

/**
 * A grid of the 10 personalities. The chosen card lifts (bigger, stronger shadow, marigold rim)
 * and the others dim. Choosing saves at once.
 */
@Composable
fun PersonalityScreen(
    appState: NovaAppState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NovaTheme.colors

    Box(modifier.fillMaxSize().nightSky(tilt = true)) {
        Column(Modifier.fillMaxSize()) {
            NovaTopBar(title = "Personality", onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = NovaSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(NovaSpacing.md),
            ) {
                Text(
                    text = "Changes take effect immediately",
                    style = NovaTheme.type.voice,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = NovaSpacing.xs),
                )
                Personality.entries.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(NovaSpacing.md),
                    ) {
                        pair.forEach { p ->
                            PersonalityCard(
                                personality = p,
                                selected = appState.personality == p,
                                onClick = { appState.applyPersonality(p) },
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
private fun PersonalityCard(
    personality: Personality,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NovaTheme.colors
    val reduced = NovaTheme.reducedMotion
    val haptic = LocalHapticFeedback.current

    val spec: AnimationSpec<Float> = if (reduced) snap() else tween(NovaMotion.NORMAL_MS)
    val lift by animateFloatAsState(if (selected) 1.04f else 1f, spec, label = "personalityLift")
    val dim by animateFloatAsState(if (selected) 1f else 0.62f, spec, label = "personalityDim")

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
                this.alpha = dim
            }
            .selectable(selected = selected, role = Role.RadioButton) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
    ) {
        NovaGlassCard(
            modifier = Modifier
                .fillMaxSize()
                .heightIn(min = 132.dp)
                .then(if (selected) Modifier.border(2.dp, colors.accent, NovaShapes.medium) else Modifier),
            elevation = if (selected) 16.dp else 6.dp,
        ) {
            Text(text = personality.icon, fontSize = 28.sp)
            Spacer(Modifier.height(NovaSpacing.sm))
            Text(personality.displayName, style = NovaTheme.type.title, color = colors.textPrimary)
            Text(personality.description, style = NovaTheme.type.caption, color = colors.textSecondary)
        }
    }
}
