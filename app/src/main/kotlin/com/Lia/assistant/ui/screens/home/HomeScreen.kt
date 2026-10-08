package com.Lia.assistant.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Lia.assistant.data.NovaAppState
import com.Lia.assistant.ui.components.NovaButton
import com.Lia.assistant.ui.components.NovaButtonStyle
import com.Lia.assistant.ui.components.NovaGlassCard
import com.Lia.assistant.ui.components.NovaOrb
import com.Lia.assistant.ui.fx.LiaOrb3D
import com.Lia.assistant.ui.fx.tiltParallax
import com.Lia.assistant.ui.theme.LocalBottomBarInset
import com.Lia.assistant.ui.theme.NovaMotion
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.ui.theme.depthSurface
import com.Lia.assistant.ui.theme.nightSky
import com.Lia.assistant.voice.NovaOrbState
import com.Lia.assistant.voice.VoiceSessionManager
import java.time.LocalTime

private val TrySaying = listOf(
    "YouTube kholo",
    "Rahim ke call koro",
    "Mayer kache message pathao",
    "Open WhatsApp",
)

private fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

/**
 * Home as a stage: night sky, a glow field tinted by the live voice state, the 3D orb as the hero,
 * and floating pills under it. [extra] is for temporary cards (the API key) below the stage.
 */
@Composable
fun HomeScreen(
    appState: NovaAppState,
    onTalk: () -> Unit,
    onType: () -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = NovaTheme.colors
    val reduced = NovaTheme.reducedMotion
    val haptic = LocalHapticFeedback.current

    val voiceState by VoiceSessionManager.state.collectAsStateWithLifecycle()
    val amplitude by VoiceSessionManager.amplitude.collectAsStateWithLifecycle()

    // Layer 1: the glow field follows the live state.
    val tintTarget = when (voiceState) {
        NovaOrbState.IDLE -> colors.lotus
        NovaOrbState.LISTENING -> colors.lagoon
        NovaOrbState.THINKING -> lerp(colors.lagoon, colors.lotus, 0.5f)
        NovaOrbState.SPEAKING -> lerp(colors.accent, colors.lotus, 0.5f)
        NovaOrbState.CONNECTING -> colors.accent
        NovaOrbState.ERROR -> colors.error
    }
    val tint by animateColorAsState(tintTarget, tween(NovaMotion.SLOW_MS), label = "homeGlow")
    val strength by animateFloatAsState(
        targetValue = if (voiceState == NovaOrbState.IDLE) 0.20f else 0.36f,
        animationSpec = tween(NovaMotion.SLOW_MS),
        label = "homeGlowStrength",
    )

    // One entrance, 600 ms: greeting first, then the orb, then everything else.
    val enter = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) enter.animateTo(1f, tween(600, easing = LinearEasing))
    }
    val segment: (Float, Float) -> Float = { from, to -> ((enter.value - from) / (to - from)).coerceIn(0f, 1f) }

    val orbSize = LocalConfiguration.current.screenWidthDp.dp * 0.55f
    val orbInteraction = remember { MutableInteractionSource() }
    val orbPressed by orbInteraction.collectIsPressedAsState()
    val orbPress by animateFloatAsState(
        targetValue = if (orbPressed && !reduced) 0.96f else 1f,
        animationSpec = tween(NovaMotion.FAST_MS),
        label = "orbPress",
    )

    Box(
        modifier
            .fillMaxSize()
            .nightSky(tilt = true),
    ) {
        // Layer 1: the glow field sits deep, so it moves least.
        Box(
            Modifier
                .fillMaxSize()
                .tiltParallax(3.dp)
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(tint.copy(alpha = strength), Color.Transparent),
                            center = Offset(size.width / 2f, size.height * 0.36f),
                            radius = size.width * 0.95f,
                        ),
                    )
                },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = NovaSpacing.xl)
                .padding(bottom = LocalBottomBarInset.current),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header: a floating glass circle with the small orb.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = NovaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .depthSurface(CircleShape, 6.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceGlass)
                        .border(1.dp, colors.surfaceBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    NovaOrb(state = voiceState, diameter = 28.dp, name = appState.assistantName)
                }
            }

            Spacer(Modifier.height(NovaSpacing.xl))

            Text(
                text = greetingFor(LocalTime.now().hour),
                style = NovaTheme.type.voice,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .tiltParallax(6.dp)
                    .graphicsLayer { alpha = segment(0f, 0.4f) },
            )

            Spacer(Modifier.height(NovaSpacing.lg))

            // Layer 3: the hero. Tap = open Voice.
            Box(
                modifier = Modifier
                    .tiltParallax(8.dp)
                    .graphicsLayer {
                        val p = segment(0.15f, 0.75f)
                        val s = (0.7f + 0.3f * p) * orbPress
                        scaleX = s
                        scaleY = s
                        alpha = p
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
                    .semantics { contentDescription = "Talk to ${appState.assistantName}" },
            ) {
                LiaOrb3D(
                    state = voiceState,
                    size = orbSize,
                    amplitude = amplitude,
                    name = appState.assistantName,
                )
            }

            Text(
                text = "Tap me to talk",
                style = NovaTheme.type.voice.copy(fontStyle = FontStyle.Italic),
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .tiltParallax(6.dp)
                    .graphicsLayer { alpha = segment(0.45f, 1f) },
            )

            Spacer(Modifier.height(NovaSpacing.xl))

            // Layer 4: floating pills.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .tiltParallax(10.dp)
                    .graphicsLayer { alpha = segment(0.45f, 1f) },
                horizontalArrangement = Arrangement.spacedBy(NovaSpacing.md),
            ) {
                NovaButton(text = "Talk", onClick = onTalk, modifier = Modifier.weight(1f))
                NovaButton(
                    text = "Type",
                    onClick = onType,
                    modifier = Modifier.weight(1f),
                    style = NovaButtonStyle.SECONDARY,
                    leadingIcon = Icons.AutoMirrored.Filled.Chat,
                )
            }

            Spacer(Modifier.height(NovaSpacing.xl))

            Text(
                text = "Try saying",
                style = NovaTheme.type.caption,
                color = colors.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = segment(0.45f, 1f) },
            )
            Spacer(Modifier.height(NovaSpacing.sm))

            val listState = rememberLazyListState()
            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = segment(0.45f, 1f) },
                horizontalArrangement = Arrangement.spacedBy(NovaSpacing.md),
                contentPadding = PaddingValues(vertical = NovaSpacing.sm),
            ) {
                itemsIndexed(TrySaying) { index, phrase ->
                    NovaGlassCard(
                        modifier = Modifier
                            .width(176.dp)
                            .graphicsLayer {
                                if (!reduced) {
                                    val info = listState.layoutInfo
                                    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                                    if (item != null) {
                                        val viewport = (info.viewportEndOffset - info.viewportStartOffset)
                                            .coerceAtLeast(1)
                                        val viewportCenter =
                                            (info.viewportStartOffset + info.viewportEndOffset) / 2f
                                        val itemCenter = item.offset + item.size / 2f
                                        val fraction = (itemCenter - viewportCenter) / viewport
                                        rotationY = (-fraction * 12f).coerceIn(-6f, 6f)
                                        cameraDistance = 12f * density
                                    }
                                }
                            },
                    ) {
                        Text(
                            text = phrase,
                            style = NovaTheme.type.body,
                            color = colors.textPrimary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(NovaSpacing.lg))
            extra()
            Spacer(Modifier.height(NovaSpacing.xl))
        }
    }
}
