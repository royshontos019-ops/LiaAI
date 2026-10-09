package com.Lia.assistant.ui.screens.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Lia.assistant.data.ApiKeyStore
import com.Lia.assistant.data.NovaAppState
import com.Lia.assistant.ui.components.NovaButton
import com.Lia.assistant.ui.fx.LiaOrb3D
import com.Lia.assistant.ui.fx.tiltParallax
import com.Lia.assistant.ui.theme.NovaMotion
import com.Lia.assistant.ui.theme.NovaShapes
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.ui.theme.depthSurface
import com.Lia.assistant.ui.theme.nightSky
import com.Lia.assistant.voice.NovaOrbState
import com.Lia.assistant.voice.VoiceForegroundService
import com.Lia.assistant.voice.VoiceSessionManager
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class VoiceStage { NEEDS_MIC, NO_KEY, LIVE }

private const val RING_BARS = 48

private fun hasMic(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/**
 * Voice Mode: the orb on the night sky and nothing else. It asks for the microphone, starts the
 * voice service and shows what the session is doing. Stop ends the session; Close only leaves
 * this screen (the session keeps running, with its notification and edge glow).
 */
@Composable
fun VoiceScreen(
    appState: NovaAppState,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit = onClose,
) {
    val context = LocalContext.current
    val colors = NovaTheme.colors
    val name = appState.assistantName

    val state by VoiceSessionManager.state.collectAsStateWithLifecycle()
    val amplitude by VoiceSessionManager.amplitude.collectAsStateWithLifecycle()
    val muted by VoiceSessionManager.muted.collectAsStateWithLifecycle()
    val statusText by VoiceSessionManager.statusText.collectAsStateWithLifecycle()
    val active by VoiceSessionManager.isActive.collectAsStateWithLifecycle()
    val lastError by VoiceSessionManager.lastError.collectAsStateWithLifecycle()

    var micGranted by remember { mutableStateOf(hasMic(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var hasKey by remember { mutableStateOf<Boolean?>(null) }

    val permissions = remember {
        if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        micGranted = hasMic(context)
    }

    // Re-check when coming back from the system settings.
    LifecycleResumeEffect(Unit) {
        micGranted = hasMic(context)
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) { hasKey = withContext(Dispatchers.IO) { ApiKeyStore.hasKey(context) } }
    LaunchedEffect(micGranted) {
        if (!micGranted && !asked) launcher.launch(permissions)
    }
    LaunchedEffect(micGranted, hasKey) {
        if (micGranted && hasKey == true && !VoiceSessionManager.isActive.value) {
            VoiceForegroundService.start(context)
        }
    }

    val stage = when {
        !micGranted -> VoiceStage.NEEDS_MIC
        hasKey == false -> VoiceStage.NO_KEY
        else -> VoiceStage.LIVE
    }
    val orbState = if (stage == VoiceStage.LIVE) state else NovaOrbState.ERROR
    val orbAmplitude = if (stage == VoiceStage.LIVE) amplitude else 0f

    val statusLine = when (stage) {
        VoiceStage.NEEDS_MIC -> "$name needs the microphone"
        VoiceStage.NO_KEY -> "$name needs a Gemini key"
        VoiceStage.LIVE -> when {
            !active -> "Starting…"
            else -> when (state) {
                NovaOrbState.IDLE -> "$name is ready"
                NovaOrbState.LISTENING -> if (muted) "Muted" else "$name is listening…"
                NovaOrbState.THINKING -> "$name is thinking…"
                NovaOrbState.SPEAKING -> "$name is speaking…"
                NovaOrbState.CONNECTING -> statusText.ifBlank { "Connecting…" }
                NovaOrbState.ERROR -> statusText.ifBlank { lastError ?: "Something went wrong" }
            }
        }
    }

    val orbSize = LocalConfiguration.current.screenWidthDp.dp * 0.70f

    Box(Modifier.fillMaxSize().nightSky(tilt = true)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = NovaSpacing.xl, vertical = NovaSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top: the assistant's name and a small minimize button.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = name, style = NovaTheme.type.headline, color = colors.textPrimary)
                Spacer(Modifier.weight(1f))
                CircleControl(
                    icon = Icons.Filled.KeyboardArrowDown,
                    label = null,
                    description = "Minimize",
                    onClick = onClose,
                    diameter = 48.dp,
                    tint = colors.textPrimary,
                    background = colors.surfaceGlass,
                )
            }

            Spacer(Modifier.weight(1f))

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(orbSize * 1.25f).tiltParallax(8.dp)) {
                LiaOrb3D(state = orbState, size = orbSize, amplitude = orbAmplitude, name = name)
                VoiceRing(
                    amplitude = orbAmplitude,
                    visible = stage == VoiceStage.LIVE && state == NovaOrbState.SPEAKING,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.height(NovaSpacing.lg))

            Text(
                text = statusLine,
                style = NovaTheme.type.voice.copy(fontStyle = FontStyle.Italic),
                color = if (orbState == NovaOrbState.ERROR) colors.error else colors.textPrimary,
                textAlign = TextAlign.Center,
            )

            when (stage) {
                VoiceStage.NEEDS_MIC -> {
                    Spacer(Modifier.height(NovaSpacing.sm))
                    Text(
                        text = "Allow the microphone so $name can hear you.",
                        style = NovaTheme.type.body,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(NovaSpacing.lg))
                    if (asked) {
                        NovaButton(text = "Open settings", onClick = { openAppSettings(context) })
                    } else {
                        NovaButton(text = "Allow microphone", onClick = { launcher.launch(permissions) })
                    }
                }
                VoiceStage.NO_KEY -> {
                    Spacer(Modifier.height(NovaSpacing.sm))
                    Text(
                        text = "Add your Gemini API key in Settings, then open Voice again.",
                        style = NovaTheme.type.body,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(NovaSpacing.lg))
                    NovaButton(text = "Open Settings", onClick = onOpenSettings)
                }
                VoiceStage.LIVE -> if (active) {
                    Spacer(Modifier.height(NovaSpacing.xs))
                    Text(
                        text = "Keeps listening if you leave this screen.",
                        style = NovaTheme.type.caption,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Controls: Mute, a big End button, and Minimize.
            val live = stage == VoiceStage.LIVE && active && state != NovaOrbState.ERROR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = NovaSpacing.sm),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                CircleControl(
                    icon = if (muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = if (muted) "Unmute" else "Mute",
                    onClick = { VoiceSessionManager.toggleMute() },
                    diameter = 60.dp,
                    tint = colors.textPrimary,
                    background = colors.surfaceGlass,
                    enabled = live,
                )
                CircleControl(
                    icon = Icons.Filled.CallEnd,
                    label = "End",
                    onClick = {
                        VoiceForegroundService.stop(context)
                        onClose()
                    },
                    diameter = 76.dp,
                    tint = Color.White,
                    background = colors.error,
                )
                CircleControl(
                    icon = Icons.Filled.KeyboardArrowDown,
                    label = "Minimize",
                    onClick = onClose,
                    diameter = 60.dp,
                    tint = colors.textPrimary,
                    background = colors.surfaceGlass,
                )
            }
        }
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/** A round button with an icon and an optional label below it. Shrinks a little when pressed. */
@Composable
private fun CircleControl(
    icon: ImageVector,
    label: String?,
    onClick: () -> Unit,
    diameter: Dp,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    val colors = NovaTheme.colors
    val reduced = NovaTheme.reducedMotion
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) 0.94f else 1f,
        animationSpec = tween(NovaMotion.FAST_MS),
        label = "controlScale",
    )
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.4f
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClickLabel = description ?: label,
                role = Role.Button,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(diameter)
                .depthSurface(CircleShape, 8.dp)
                .clip(CircleShape)
                .background(background)
                .border(1.dp, colors.surfaceBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(diameter * 0.42f))
        }
        if (label != null) {
            Spacer(Modifier.height(NovaSpacing.xs))
            Text(text = label, style = NovaTheme.type.caption, color = colors.textSecondary)
        }
    }
}

/** 48 bars around the orb that follow the voice level. Only drawn while Lia is speaking. */
@Composable
private fun VoiceRing(
    amplitude: Float,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = NovaTheme.colors
    val reduced = NovaTheme.reducedMotion
    val level by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 1f),
        animationSpec = tween(NovaMotion.FAST_MS),
        label = "ringLevel",
    )
    val shown by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(NovaMotion.SLOW_MS),
        label = "ringShown",
    )
    val transition = rememberInfiniteTransition(label = "ringWave")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "ringPhase",
    )
    val lagoon = colors.lagoon
    val lotus = colors.lotus

    Canvas(modifier) {
        if (shown <= 0.01f) return@Canvas
        val c = Offset(size.width / 2f, size.height / 2f)
        val ringRadius = size.minDimension * 0.368f
        val maxLength = size.minDimension * 0.096f
        val stroke = 3.dp.toPx()
        val phase = if (reduced) 0f else wave
        for (i in 0 until RING_BARS) {
            val angle = i * (2f * PI.toFloat() / RING_BARS) - PI.toFloat() / 2f
            val ripple = if (reduced) 1f else 0.55f + 0.45f * abs(sin(phase + i * 0.5f))
            val length = maxLength * (0.12f + 0.88f * level * ripple)
            val dx = cos(angle)
            val dy = sin(angle)
            drawLine(
                color = lerp(lagoon, lotus, i / (RING_BARS - 1f)).copy(alpha = 0.9f * shown),
                start = Offset(c.x + dx * ringRadius, c.y + dy * ringRadius),
                end = Offset(c.x + dx * (ringRadius + length), c.y + dy * (ringRadius + length)),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
