package com.Lia.assistant.ui.fx

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.Lia.assistant.ui.theme.NovaMotion
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.voice.NovaOrbState
import kotlinx.coroutines.flow.MutableStateFlow

/** What the border should say: resting (listening), Lia speaking, or a problem. */
enum class EdgeGlowMode { RESTING, SPEAKING, ERROR }

/**
 * Small shared switchboard between the voice service and the two places that draw the glow
 * (the in-app dialog and, in the direct flavor, the overlay window).
 */
object EdgeGlowBus {
    /** True while a voice session is running. */
    val active = MutableStateFlow(false)

    /** True while the app's own screen is in front. The overlay stays quiet then. */
    val appVisible = MutableStateFlow(false)

    val mode = MutableStateFlow(EdgeGlowMode.RESTING)

    fun setFromOrb(state: NovaOrbState) {
        mode.value = when (state) {
            NovaOrbState.SPEAKING -> EdgeGlowMode.SPEAKING
            NovaOrbState.ERROR -> EdgeGlowMode.ERROR
            else -> EdgeGlowMode.RESTING
        }
    }
}

private val GlowViolet = Color(0xFF8B5CF6)
private val GlowPink = Color(0xFFFF6FAE)
private val GlowRed = Color(0xFFFF7A85)

/**
 * A slim glowing border around the whole screen. It only draws; whoever hosts it decides
 * which window it lives in.
 */
@Composable
fun ListeningEdgeGlow(
    mode: EdgeGlowMode,
    modifier: Modifier = Modifier,
    thickness: Dp = 26.dp,
    reducedMotion: Boolean = NovaTheme.reducedMotion,
) {
    val target = when (mode) {
        EdgeGlowMode.RESTING -> GlowViolet
        EdgeGlowMode.SPEAKING -> GlowPink
        EdgeGlowMode.ERROR -> GlowRed
    }
    val color by animateColorAsState(target, tween(NovaMotion.SLOW_MS), label = "glowColor")

    val periodMs = when (mode) {
        EdgeGlowMode.RESTING -> 2800
        EdgeGlowMode.SPEAKING -> 700
        EdgeGlowMode.ERROR -> 1400
    }
    val transition = rememberInfiniteTransition(label = "edgeGlow")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Reverse),
        label = "glowPulse",
    )

    Canvas(modifier.fillMaxSize()) {
        val t = thickness.toPx()
        val w = this.size.width
        val h = this.size.height
        val a = if (reducedMotion) 0.8f else pulse
        val edge = color.copy(alpha = 0.85f * a)
        val clear = color.copy(alpha = 0f)

        drawRect(
            brush = Brush.verticalGradient(listOf(edge, clear), startY = 0f, endY = t),
            topLeft = Offset.Zero,
            size = Size(w, t),
        )
        drawRect(
            brush = Brush.verticalGradient(listOf(clear, edge), startY = h - t, endY = h),
            topLeft = Offset(0f, h - t),
            size = Size(w, t),
        )
        drawRect(
            brush = Brush.horizontalGradient(listOf(edge, clear), startX = 0f, endX = t),
            topLeft = Offset.Zero,
            size = Size(t, h),
        )
        drawRect(
            brush = Brush.horizontalGradient(listOf(clear, edge), startX = w - t, endX = w),
            topLeft = Offset(w - t, 0f),
            size = Size(t, h),
        )
    }
}

/**
 * Hosts the glow in a full-screen Dialog window, so it sits above every screen of the app.
 * The window cannot be touched and does not take focus, so the app below works as usual.
 * Call it once from MainActivity.
 */
@Composable
fun EdgeGlowDialogHost() {
    val active by EdgeGlowBus.active.collectAsState()
    val appVisible by EdgeGlowBus.appVisible.collectAsState()
    val mode by EdgeGlowBus.mode.collectAsState()
    if (!active || !appVisible) return

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                )
            }
        }
        ListeningEdgeGlow(mode = mode, modifier = Modifier.fillMaxSize())
    }
}
