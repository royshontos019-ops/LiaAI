package com.Lia.assistant.ui.screens.forge

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.Lia.assistant.forge.ForgePhase
import com.Lia.assistant.forge.ForgeStage
import com.Lia.assistant.forge.ForgeUiState
import com.Lia.assistant.ui.theme.NovaTheme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The same story as the 3D scene, drawn with a plain Canvas: a glowing core, a ring of six stage
 * nodes, an arc that fills with progress, orbiting glyph dots and one glass slab per section.
 * Used on low-memory phones or when WebGL is not available.
 */
@Composable
fun ForgeFallbackScene(
    state: ForgeUiState,
    reveal: Boolean,
    onRevealFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NovaTheme.colors
    val reduced = NovaTheme.reducedMotion
    val finished by rememberUpdatedState(onRevealFinished)
    val failed = state.phase == ForgePhase.FAILED

    val transition = rememberInfiniteTransition(label = "forgeFallback")
    val spin by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "spin")
    val pulse by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1_600), RepeatMode.Reverse), label = "pulse")
    val angle = if (reduced) 0f else spin
    val breathe = if (reduced) 0.5f else pulse

    val revealAnim = remember { Animatable(0f) }
    LaunchedEffect(reveal) {
        if (reveal) {
            revealAnim.animateTo(1f, tween(if (reduced) 100 else 900))
            finished()
        }
    }
    val flash = revealAnim.value

    val muted = colors.textTertiary
    val core1 = if (failed) muted else colors.lagoon
    val core2 = if (failed) muted else colors.lotus
    val accent = if (failed) muted else colors.accent
    val stageCount = ForgeStage.entries.size
    val stage = state.stage.ordinal
    val sections = min(state.sections.size, 10)

    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(colors.backgroundGradientTop, colors.background)))
        val c = Offset(size.width / 2f, size.height * 0.4f)
        val unit = min(size.width, size.height)

        // glow and core
        val coreR = unit * (0.1f + 0.012f * breathe + 0.2f * flash) * (0.7f + 0.5f * state.progress)
        drawCircle(Brush.radialGradient(listOf(core2.copy(alpha = 0.45f), Color.Transparent), c, unit * 0.5f), unit * 0.5f, c)
        drawCircle(Brush.radialGradient(listOf(core1, core2.copy(alpha = 0.85f), Color.Transparent), c, coreR * 1.4f), coreR * 1.4f, c)

        // three gyro rings
        for (i in 0 until 3) {
            rotate(degrees = angle * (1f + i * 0.5f) + i * 60f, pivot = c) {
                drawOval(
                    color = (if (i == 1) core1 else accent).copy(alpha = 0.55f),
                    topLeft = Offset(c.x - unit * (0.2f + i * 0.04f), c.y - unit * (0.07f + i * 0.02f)),
                    size = Size(unit * (0.4f + i * 0.08f), unit * (0.14f + i * 0.04f)),
                    style = Stroke(width = 2f),
                )
            }
        }

        // glyph dots spiralling in
        val dots = 36
        for (i in 0 until dots) {
            val u = ((i / dots.toFloat()) + angle / 360f * 0.6f) % 1f
            val r = unit * (0.45f - 0.38f * u * u)
            val a = Math.toRadians((i * 47.0 + angle * 1.5 + u * 280.0))
            drawCircle(
                color = (if (i % 2 == 0) core1 else accent).copy(alpha = (1f - u) * 0.8f * (0.3f + state.progress)),
                radius = 3f + 3f * (1f - u),
                center = Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r * 0.55f),
            )
        }

        // glass slabs, one per section
        for (i in 0 until sections) {
            val a = Math.toRadians(-110.0 + i * (220.0 / 9.0))
            val w = unit * 0.16f
            val h = unit * 0.21f
            val sc = Offset(c.x + cos(a).toFloat() * unit * 0.4f, c.y - sin(a).toFloat() * unit * 0.26f)
            val fuse = flash
            val px = sc.x + (c.x - sc.x) * fuse
            val py = sc.y + (c.y - sc.y) * fuse
            drawRoundRect(
                color = (if (i % 2 == 0) accent else core1).copy(alpha = (0.18f + 0.1f * breathe) * (1f - fuse * 0.6f)),
                topLeft = Offset(px - w / 2f, py - h / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(14f),
            )
            drawRoundRect(
                color = (if (i % 2 == 0) accent else core1).copy(alpha = 0.7f * (1f - fuse * 0.6f)),
                topLeft = Offset(px - w / 2f, py - h / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(14f),
                style = Stroke(width = 2f),
            )
        }

        // stage ring: nodes and the filled arc
        val ringTop = Offset(c.x - unit * 0.34f, size.height * 0.68f - unit * 0.09f)
        val ringSize = Size(unit * 0.68f, unit * 0.18f)
        drawOval(muted.copy(alpha = 0.4f), ringTop, ringSize, style = Stroke(width = 3f))
        drawArc(
            color = accent,
            startAngle = -90f,
            sweepAngle = 360f * state.progress,
            useCenter = false,
            topLeft = ringTop,
            size = ringSize,
            style = Stroke(width = 5f),
        )
        for (i in 0 until stageCount) {
            val a = Math.toRadians(-90.0 + (i + 0.5) / stageCount * 360.0)
            val p = Offset(
                ringTop.x + ringSize.width / 2f + cos(a).toFloat() * ringSize.width / 2f,
                ringTop.y + ringSize.height / 2f + sin(a).toFloat() * ringSize.height / 2f,
            )
            drawCircle(
                color = when {
                    i < stage -> accent
                    i == stage -> core1
                    else -> muted
                },
                radius = if (i == stage) 9f + 3f * breathe else 6f,
                center = p,
            )
        }

        // reveal flash
        if (flash > 0f) drawRect(core2.copy(alpha = (1f - flash) * 0.35f * flash * 4f))
    }
}
