package com.Lia.assistant.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.Lia.assistant.data.AssistantBrand
import com.Lia.assistant.ui.fx.LiaOrb3D
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.voice.NovaOrbState
import androidx.compose.material3.Text
import kotlinx.coroutines.delay

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

/**
 * Short 3D intro drawn over the app while it loads: the orb grows in, thinks for a moment,
 * speaks once and fades away. [onFinished] is called when it is gone, so the caller can remove it.
 */
@Composable
fun LiaSplash3D(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NovaTheme.colors
    val reduced = NovaTheme.reducedMotion
    val finished = rememberUpdatedState(onFinished)

    val appear = remember { Animatable(0f) }
    val amp = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    var orbState by remember { mutableStateOf(NovaOrbState.CONNECTING) }

    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(if (reduced) 1 else 800, easing = FastOutSlowInEasing))
        orbState = NovaOrbState.THINKING
        delay(if (reduced) 300 else 600)
        orbState = NovaOrbState.SPEAKING
        if (!reduced) {
            amp.animateTo(0.85f, tween(260))
            amp.animateTo(0.25f, tween(380))
        }
        delay(250)
        fade.animateTo(0f, tween(if (reduced) 150 else 450))
        finished.value()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(colors.background)
            .pointerInput(Unit) {
                // Swallow touches so nothing underneath reacts while the intro is on screen.
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LiaOrb3D(
            state = orbState,
            size = 240.dp,
            amplitude = amp.value,
            modifier = Modifier.graphicsLayer {
                val s = 0.6f + 0.4f * appear.value
                scaleX = s
                scaleY = s
                alpha = appear.value
            },
        )
        Text(
            text = AssistantBrand.NAME,
            style = NovaTheme.type.display,
            color = colors.textPrimary,
            modifier = Modifier
                .graphicsLayer { alpha = appear.value }
                .padding(top = NovaSpacing.lg),
        )
    }
}
