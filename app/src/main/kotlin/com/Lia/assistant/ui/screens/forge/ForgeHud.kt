package com.Lia.assistant.ui.screens.forge

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Lia.assistant.forge.ForgePhase
import com.Lia.assistant.forge.ForgeStage
import com.Lia.assistant.forge.ForgeUiState
import com.Lia.assistant.ui.components.NovaButton
import com.Lia.assistant.ui.components.NovaButtonStyle
import com.Lia.assistant.ui.components.NovaGlassCard
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import kotlin.math.roundToInt

/** The two quiet glass pieces over the scene: the prompt on top, the status card at the bottom. */
@Composable
fun ForgeHud(
    state: ForgeUiState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NovaTheme.colors
    val reduced = NovaTheme.reducedMotion
    val haptic = LocalHapticFeedback.current

    // A small tap each time a new stage begins (not for the first one).
    var lastStage by remember { mutableIntStateOf(state.stage.ordinal) }
    LaunchedEffect(state.stage) {
        if (state.stage.ordinal != lastStage) {
            lastStage = state.stage.ordinal
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val percent = (state.progress * 100f).roundToInt().coerceIn(0, 100)
    val building = state.phase == ForgePhase.BUILDING
    val failed = state.phase == ForgePhase.FAILED
    val cancelled = state.phase == ForgePhase.CANCELLED
    val done = state.phase == ForgePhase.DONE

    Box(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = NovaSpacing.lg, vertical = NovaSpacing.md)) {
        // Top: what is being built.
        NovaGlassCard(Modifier.fillMaxWidth().align(Alignment.TopCenter), elevation = 8.dp) {
            Text(
                text = if (state.isEdit) "Changing: ${state.prompt}" else state.prompt,
                style = NovaTheme.type.body,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Bottom: status.
        NovaGlassCard(Modifier.fillMaxWidth().align(Alignment.BottomCenter), elevation = 8.dp) {
            Column(
                Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = when {
                        failed -> "Could not build the website. ${state.error.orEmpty()}"
                        cancelled -> "Cancelled"
                        done -> "Website ready"
                        else -> "Building website, $percent percent, ${state.stage.label}"
                    }
                },
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        val label = when {
                            failed -> "Stopped"
                            cancelled -> "Cancelled"
                            done -> "Ready"
                            else -> state.stage.label
                        }
                        if (reduced || !building) {
                            Text(label, style = NovaTheme.type.title, color = colors.textPrimary)
                        } else {
                            AnimatedContent(
                                targetState = label,
                                transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
                                label = "stageLabel",
                            ) { text -> Text(text, style = NovaTheme.type.title, color = colors.textPrimary) }
                        }
                    }
                    Text(
                        text = "$percent%",
                        style = NovaTheme.type.voice.copy(fontSize = 40.sp),
                        color = if (failed) colors.error else colors.accent,
                    )
                }

                Spacer(Modifier.height(NovaSpacing.sm))
                StageTrack(state)
                Spacer(Modifier.height(NovaSpacing.sm))

                if (failed || cancelled) {
                    if (failed && !state.error.isNullOrBlank()) {
                        Text(state.error, style = NovaTheme.type.body, color = colors.textSecondary)
                        Spacer(Modifier.height(NovaSpacing.sm))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(NovaSpacing.md)) {
                        NovaButton(text = "Try again", onClick = onRetry, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                        NovaButton(text = "Close", onClick = onClose, style = NovaButtonStyle.SECONDARY, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("${state.metrics.chars / 1000} KB")
                        Stat("${state.metrics.sections} sections")
                        Stat("${state.metrics.tokensPerSec.roundToInt()} tok/s")
                        Stat("${state.metrics.elapsedSec}s")
                    }
                    if (building) {
                        Spacer(Modifier.height(NovaSpacing.md))
                        NovaButton(
                            text = "Cancel",
                            onClick = onCancel,
                            style = NovaButtonStyle.SECONDARY,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(text: String) {
    Text(text, style = NovaTheme.type.caption, color = NovaTheme.colors.textSecondary)
}

/** Six segments, one per stage: finished ones full, the current one filling with progress. */
@Composable
private fun StageTrack(state: ForgeUiState) {
    val colors = NovaTheme.colors
    val count = ForgeStage.entries.size
    val current = state.stage.ordinal
    val done = state.phase == ForgePhase.DONE
    Row(Modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until count) {
            val fill = when {
                done || i < current -> 1f
                i == current -> (state.progress * count - i).coerceIn(0.15f, 1f)
                else -> 0f
            }
            val color = if (state.phase == ForgePhase.FAILED) colors.error else colors.accent
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(colors.surfaceBorder)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(color))
            }
        }
    }
}
