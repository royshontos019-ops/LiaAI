package com.Lia.assistant.ui.screens.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.Lia.assistant.ui.components.NovaButton
import com.Lia.assistant.ui.components.NovaButtonStyle
import com.Lia.assistant.ui.fx.LiaOrb3D
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.ui.theme.nightSky
import com.Lia.assistant.voice.NovaOrbState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

private class OnboardingPage(val title: String, val body: String)

private val Pages = listOf(
    OnboardingPage("Meet Lia", "A pearl of light in the night. Your voice assistant, always one tap away."),
    OnboardingPage("Talk or type", "Say it out loud, or write it down. Lia opens apps, calls, messages and more."),
    OnboardingPage("Two halves, one Lia", "Voice and chat share the same memory, so you can switch any time."),
    OnboardingPage("Private by design", "You choose what Lia can reach. Every permission can be changed later."),
)

/**
 * First-run intro: 4 pages, the orb in a different pose on each. The page content moves at 0.6x
 * the pager scroll, so the orb and the text drift against each other. [onFinished] is called on
 * Skip or on the last page's button; the caller remembers that it was seen.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NovaTheme.colors
    val reduced = NovaTheme.reducedMotion
    val pagerState = rememberPagerState(pageCount = { Pages.size })
    val scope = rememberCoroutineScope()
    val width = LocalConfiguration.current.screenWidthDp.dp
    val orbSize = width * 0.5f
    val last = pagerState.currentPage == Pages.size - 1

    Box(
        modifier
            .fillMaxSize()
            .nightSky(tilt = true)
            .pointerInput(Unit) {}, // swallow touches so Home underneath cannot be tapped
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NovaSpacing.lg, vertical = NovaSpacing.sm),
                horizontalArrangement = Arrangement.End,
            ) {
                if (!last) NovaButton(text = "Skip", onClick = onFinished, style = NovaButtonStyle.TEXT)
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = NovaSpacing.xl)
                        .graphicsLayer {
                            // Pager moves the page by -offset * W; adding 0.4 * offset * W leaves 0.6x.
                            if (!reduced) translationX = offset * size.width * 0.4f
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    PosedOrb(page = page, size = orbSize, reduced = reduced)
                    Spacer(Modifier.height(NovaSpacing.xl))
                    Text(
                        text = Pages[page].title,
                        style = NovaTheme.type.voice,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(NovaSpacing.md))
                    Text(
                        text = Pages[page].body,
                        style = NovaTheme.type.body.copy(fontStyle = FontStyle.Italic),
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = NovaSpacing.md),
            ) {
                repeat(Pages.size) { i ->
                    val selected = i == pagerState.currentPage
                    val w by animateDpAsState(if (selected) 24.dp else 8.dp, tween(200), label = "dotW")
                    Box(
                        Modifier
                            .size(width = w, height = 8.dp)
                            .clip(CircleShape)
                            .background(if (selected) colors.accent else colors.textSecondary.copy(alpha = 0.4f)),
                    )
                }
            }

            NovaButton(
                text = if (last) "Get started" else "Next",
                onClick = {
                    if (last) onFinished()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NovaSpacing.xl)
                    .padding(bottom = NovaSpacing.lg),
            )
        }
    }
}

/** Page 0 large centre, 1 orbiting, 2 split in two halves, 3 shielded by a ring. */
@Composable
private fun PosedOrb(page: Int, size: Dp, reduced: Boolean) {
    val colors = NovaTheme.colors
    val transition = rememberInfiniteTransition(label = "onboardingOrb")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "phase",
    )
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    val p = if (reduced) 0f else phase
    val b = if (reduced) 0f else breathe

    when (page) {
        0 -> Box(Modifier.size(size * 1.2f), contentAlignment = Alignment.Center) {
            LiaOrb3D(state = NovaOrbState.IDLE, size = size * (1f + 0.04f * b))
        }

        1 -> {
            val dot = colors.lagoon
            val dot2 = colors.accent
            Box(
                Modifier
                    .size(size * 1.2f)
                    .drawBehind {
                        val r = this.size.minDimension * 0.46f
                        val c = Offset(this.size.width / 2f, this.size.height / 2f)
                        for (k in 0 until 3) {
                            val a = (p + k / 3f) * 2f * PI.toFloat()
                            drawCircle(
                                color = if (k == 1) dot2 else dot,
                                radius = 7.dp.toPx(),
                                center = Offset(c.x + r * cos(a), c.y + r * sin(a)),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                LiaOrb3D(state = NovaOrbState.LISTENING, size = size * 0.7f)
            }
        }

        2 -> {
            val gap = if (reduced) 8f else 8f + 14f * b
            Box(Modifier.size(size * 1.2f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(size)
                        .graphicsLayer { translationX = -gap.dp.toPx() }
                        .drawWithContent {
                            clipRect(left = 0f, right = this.size.width / 2f) {
                                this@drawWithContent.drawContent()
                            }
                        },
                ) { LiaOrb3D(state = NovaOrbState.IDLE, size = size) }
                Box(
                    Modifier
                        .size(size)
                        .graphicsLayer { translationX = gap.dp.toPx() }
                        .drawWithContent {
                            clipRect(left = this.size.width / 2f, right = this.size.width) {
                                this@drawWithContent.drawContent()
                            }
                        },
                ) { LiaOrb3D(state = NovaOrbState.SPEAKING, size = size) }
            }
        }

        else -> {
            val ring = colors.accent
            Box(
                Modifier
                    .size(size * 1.2f)
                    .drawBehind {
                        val c = Offset(this.size.width / 2f, this.size.height / 2f)
                        val r = this.size.minDimension * 0.48f
                        drawCircle(
                            color = ring.copy(alpha = 0.25f + 0.2f * b),
                            radius = r + 6.dp.toPx(),
                            center = c,
                            style = Stroke(width = 6.dp.toPx()),
                        )
                        drawCircle(
                            color = ring,
                            radius = r,
                            center = c,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                LiaOrb3D(state = NovaOrbState.IDLE, size = size * 0.78f)
            }
        }
    }
}
