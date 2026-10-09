package com.Lia.assistant.ui.screens.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.Lia.assistant.data.NovaAppState
import com.Lia.assistant.ui.components.NovaChoiceChips
import com.Lia.assistant.ui.components.NovaOrb
import com.Lia.assistant.ui.components.NovaOrbStyle
import com.Lia.assistant.ui.components.NovaTopBar
import com.Lia.assistant.ui.fx.tiltParallax
import com.Lia.assistant.ui.theme.NovaShapes
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.ui.theme.depthSurface
import com.Lia.assistant.ui.theme.nightSky
import com.Lia.assistant.voice.NovaOrbState
import kotlin.math.abs
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Turns the saved text into a style. Anything unknown (like the old default "classic") is NOVA. */
fun orbStyleFromId(id: String?): NovaOrbStyle =
    NovaOrbStyle.entries.firstOrNull { it.name.equals(id?.trim(), ignoreCase = true) } ?: NovaOrbStyle.NOVA

fun orbStyleLabel(style: NovaOrbStyle): String = when (style) {
    NovaOrbStyle.NOVA -> "Nova"
    NovaOrbStyle.AURORA -> "Aurora"
    NovaOrbStyle.PLASMA -> "Plasma"
    NovaOrbStyle.GLASS -> "Glass"
    NovaOrbStyle.ENERGY -> "Energy"
    NovaOrbStyle.MINIMAL -> "Minimal"
    NovaOrbStyle.ARCHER -> "Archer"
}

private fun orbStyleHint(style: NovaOrbStyle): String = when (style) {
    NovaOrbStyle.NOVA -> "The classic pearl"
    NovaOrbStyle.AURORA -> "Cool, wide glow"
    NovaOrbStyle.PLASMA -> "Warm, lively particles"
    NovaOrbStyle.GLASS -> "Clear and shiny"
    NovaOrbStyle.ENERGY -> "Fast and bright"
    NovaOrbStyle.MINIMAL -> "Simple and calm"
    NovaOrbStyle.ARCHER -> "A sphere of tiny dots"
}

private fun previewLabel(state: NovaOrbState): String = when (state) {
    NovaOrbState.IDLE -> "Idle"
    NovaOrbState.LISTENING -> "Listening"
    NovaOrbState.SPEAKING -> "Speaking"
    else -> state.name.lowercase().replaceFirstChar { it.uppercase() }
}

private val TileWidth = 124.dp
private val TileHeight = 156.dp

/**
 * A live preview of the chosen orb with Idle / Listening / Speaking toggles, and a snapping
 * coverflow carousel of the 7 styles. The tile that stops in the centre is the chosen one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OrbStyleScreen(
    appState: NovaAppState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NovaTheme.colors
    val reduced = NovaTheme.reducedMotion
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val styles = remember { NovaOrbStyle.entries.toList() }
    val selected = orbStyleFromId(appState.orbStyle)
    var previewState by remember { mutableStateOf(NovaOrbState.IDLE) }

    // A gentle fake voice level, so Speaking and Listening show their motion.
    val wave by rememberInfiniteTransition(label = "previewWave").animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "previewWaveValue",
    )
    val amplitude = when {
        reduced -> 0f
        previewState == NovaOrbState.SPEAKING -> wave
        previewState == NovaOrbState.LISTENING -> wave * 0.4f
        else -> 0f
    }

    // Starting at the saved style puts that tile in the middle (the side padding centres it).
    val sidePadding = ((LocalConfiguration.current.screenWidthDp.dp - TileWidth) / 2f).coerceAtLeast(0.dp)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = styles.indexOf(selected).coerceAtLeast(0),
    )

    // When the carousel stops moving, whichever tile is nearest the middle becomes the style.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect {
                val info = listState.layoutInfo
                val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
                val nearest = info.visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2) - middle) }
                val style = nearest?.let { styles.getOrNull(it.index) }
                if (style != null && style != orbStyleFromId(appState.orbStyle)) {
                    appState.applyOrbStyle(style.name.lowercase())
                }
            }
    }

    Box(modifier.fillMaxSize().nightSky(tilt = true)) {
        Column(Modifier.fillMaxSize()) {
            NovaTopBar(title = "Orb style", onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(NovaSpacing.md))

                Box(
                    modifier = Modifier.size(240.dp).tiltParallax(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    NovaOrb(
                        state = previewState,
                        diameter = 240.dp,
                        amplitude = amplitude,
                        style = selected,
                        name = appState.assistantName,
                    )
                }

                Spacer(Modifier.height(NovaSpacing.md))

                Box(Modifier.padding(horizontal = NovaSpacing.xl)) {
                    NovaChoiceChips(
                        options = listOf(NovaOrbState.IDLE, NovaOrbState.LISTENING, NovaOrbState.SPEAKING),
                        selected = previewState,
                        label = { previewLabel(it) },
                        onSelect = { previewState = it },
                    )
                }

                Spacer(Modifier.height(NovaSpacing.xl))

                Text(
                    text = "Swipe to choose",
                    style = NovaTheme.type.voice,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )

                LazyRow(
                    state = listState,
                    flingBehavior = rememberSnapFlingBehavior(listState),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = sidePadding, vertical = NovaSpacing.xl),
                    horizontalArrangement = Arrangement.spacedBy(NovaSpacing.md),
                ) {
                    itemsIndexed(styles) { index, style ->
                        StyleTile(
                            index = index,
                            style = style,
                            chosen = style == selected,
                            listState = listState,
                            reduced = reduced,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                appState.applyOrbStyle(style.name.lowercase())
                                scope.launch { listState.animateScrollToItem(index) }
                            },
                        )
                    }
                }

                Text(
                    text = orbStyleLabel(selected),
                    style = NovaTheme.type.headline,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = orbStyleHint(selected),
                    style = NovaTheme.type.caption,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(NovaSpacing.xxl))
            }
        }
    }
}

/** One tile of the coverflow: the one in the middle is biggest and faces front, the others tilt in. */
@Composable
private fun StyleTile(
    index: Int,
    style: NovaOrbStyle,
    chosen: Boolean,
    listState: LazyListState,
    reduced: Boolean,
    onClick: () -> Unit,
) {
    val colors = NovaTheme.colors
    val shape = NovaShapes.large

    Column(
        modifier = Modifier
            .width(TileWidth)
            .height(TileHeight)
            .graphicsLayer {
                if (!reduced) {
                    val info = listState.layoutInfo
                    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                    if (item != null) {
                        val viewportMiddle = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                        val itemMiddle = item.offset + item.size / 2f
                        val fraction = (itemMiddle - viewportMiddle) / item.size.coerceAtLeast(1)
                        val distance = abs(fraction).coerceAtMost(1.5f)
                        val s = (1.18f - 0.22f * distance).coerceAtLeast(0.78f)
                        scaleX = s
                        scaleY = s
                        rotationY = (-fraction * 35f).coerceIn(-35f, 35f)
                        cameraDistance = 12f * density
                        alpha = (1f - 0.4f * distance).coerceIn(0.45f, 1f)
                    }
                }
            }
            .depthSurface(shape, if (chosen) 14.dp else 6.dp)
            .clip(shape)
            .background(colors.surfaceGlass)
            .border(
                width = if (chosen) 2.dp else 1.dp,
                color = if (chosen) colors.accent else colors.surfaceBorder,
                shape = shape,
            )
            .selectable(selected = chosen, role = Role.RadioButton, onClick = onClick)
            .padding(NovaSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NovaOrb(state = NovaOrbState.IDLE, diameter = 92.dp, style = style)
        Spacer(Modifier.height(NovaSpacing.xs))
        Text(
            text = orbStyleLabel(style),
            style = NovaTheme.type.label,
            color = if (chosen) colors.accent else colors.textPrimary,
        )
    }
}
