package com.Lia.assistant.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.Lia.assistant.data.AssistantBrand
import com.Lia.assistant.ui.fx.LiaOrb3D
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.ui.theme.nightSky
import com.Lia.assistant.voice.NovaOrbState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Remembers, for the life of the process, that the intro has been claimed. */
object SplashSession {
    @Volatile
    var played: Boolean = false
        private set

    /** True only for the very first caller. Rotation and resume get false. */
    @Synchronized
    fun claim(): Boolean {
        if (played) return false
        played = true
        return true
    }
}

private const val TWO_PI = (2.0 * PI).toFloat()

private class Streak(val angle: Float, val start: Float, val speed: Float, val length: Float, val width: Float)
private class Mote(val angle: Float, val radius: Float, val size: Float, val spin: Float, val warm: Boolean)

private fun segment(value: Float, from: Float, to: Float): Float = ((value - from) / (to - from)).coerceIn(0f, 1f)

/**
 * The 3D intro, 2.2 s at most: stars streak past, specks of light gather in the middle, the orb
 * condenses out of them, the name appears, then everything dissolves into Home. Tap to skip.
 * [onFinished] is called when it is gone, so the caller can remove it.
 */
@Composable
fun LiaSplash3D(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NovaTheme.colors
    val reduced = NovaTheme.reducedMotion
    val finished = rememberUpdatedState(onFinished)
    val totalMs = if (reduced) 700 else 2200

    val streaks = remember {
        val r = Random(11)
        List(64) {
            Streak(
                angle = r.nextFloat() * TWO_PI,
                start = 0.04f + r.nextFloat() * 0.25f,
                speed = 0.5f + r.nextFloat() * 0.9f,
                length = 0.05f + r.nextFloat() * 0.12f,
                width = 0.6f + r.nextFloat() * 1.4f,
            )
        }
    }
    val motes = remember {
        val r = Random(23)
        List(90) {
            Mote(
                angle = r.nextFloat() * TWO_PI,
                radius = 0.35f + r.nextFloat() * 0.6f,
                size = 1f + r.nextFloat() * 1.8f,
                spin = 0.6f + r.nextFloat() * 1.0f,
                warm = r.nextBoolean(),
            )
        }
    }

    val t = remember { Animatable(0f) }
    val amp = remember { Animatable(0f) }
    val skipFade = remember { Animatable(1f) }
    var orbState by remember { mutableStateOf(NovaOrbState.CONNECTING) }
    var skipped by remember { mutableStateOf(false) }
    val doneFlag = remember { booleanArrayOf(false) }
    val finishOnce = {
        if (!doneFlag[0]) {
            doneFlag[0] = true
            finished.value()
        }
    }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                if (!reduced) {
                    delay((totalMs * 0.62f).toLong())
                    orbState = NovaOrbState.SPEAKING
                    amp.animateTo(0.85f, tween(220))
                    amp.animateTo(0.2f, tween(350))
                }
            }
            t.animateTo(1f, tween(totalMs, easing = LinearEasing))
        }
        if (!skipped) finishOnce()
    }
    LaunchedEffect(skipped) {
        if (skipped) {
            skipFade.animateTo(0f, tween(180))
            finishOnce()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = min(1f - segment(t.value, 0.82f, 1f), skipFade.value) }
            .nightSky()
            .pointerInput(Unit) { detectTapGestures { skipped = true } },
    ) {
        if (!reduced) {
            Canvas(Modifier.fillMaxSize()) {
                val v = t.value
                val c = Offset(size.width / 2f, size.height / 2f)
                val maxR = hypot(size.width, size.height) / 2f
                val minDim = size.minDimension

                // Stars streaking outward.
                val sp = segment(v, 0f, 0.5f)
                if (sp > 0f && sp < 1f) {
                    val fade = 1f - segment(v, 0.35f, 0.5f)
                    for (s in streaks) {
                        val head = (s.start + s.speed * sp * sp * 1.6f) * maxR
                        val tail = max(0f, head - s.length * maxR * (0.3f + sp))
                        val dx = cos(s.angle)
                        val dy = sin(s.angle)
                        drawLine(
                            color = colors.textPrimary.copy(alpha = (0.15f + 0.6f * sp) * fade),
                            start = Offset(c.x + dx * tail, c.y + dy * tail),
                            end = Offset(c.x + dx * head, c.y + dy * head),
                            strokeWidth = s.width.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }

                // Specks of light gathering into the middle.
                val gather = segment(v, 0.22f, 0.62f)
                if (gather > 0f && v < 0.66f) {
                    val eased = 1f - (1f - gather) * (1f - gather) * (1f - gather)
                    val vanish = 1f - segment(v, 0.58f, 0.66f)
                    for (m in motes) {
                        val radius = m.radius * minDim * 0.6f * (1f - eased)
                        val angle = m.angle + m.spin * (1f - eased) * 2.2f
                        drawCircle(
                            color = (if (m.warm) colors.lotus else colors.lagoon)
                                .copy(alpha = (0.2f + 0.8f * gather) * vanish),
                            radius = m.size.dp.toPx(),
                            center = Offset(c.x + cos(angle) * radius, c.y + sin(angle) * radius),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LiaOrb3D(
                state = orbState,
                size = 240.dp,
                amplitude = amp.value,
                modifier = Modifier.graphicsLayer {
                    val p = segment(t.value, 0.5f, 0.78f)
                    val s = 0.4f + 0.6f * p
                    scaleX = s
                    scaleY = s
                    alpha = p
                },
            )
            Text(
                text = AssistantBrand.NAME,
                style = NovaTheme.type.display,
                color = colors.textPrimary,
                modifier = Modifier
                    .graphicsLayer { alpha = segment(t.value, 0.7f, 0.85f) }
                    .padding(top = NovaSpacing.lg),
            )
        }

        Text(
            text = "Tap to skip",
            style = NovaTheme.type.caption,
            color = colors.textSecondary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = NovaSpacing.xl)
                .graphicsLayer { alpha = 0.7f * (1f - segment(t.value, 0.6f, 0.8f)) },
        )
    }
}
