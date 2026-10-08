package com.Lia.assistant.ui.fx

import android.content.Context
import android.graphics.RuntimeShader
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.Lia.assistant.data.AssistantBrand
import com.Lia.assistant.ui.components.NovaOrb
import com.Lia.assistant.ui.theme.NovaMotion
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.voice.NovaOrbState
import kotlin.math.PI
import kotlin.math.sin

/**
 * Lia's 3D pearl: a lit sphere drawn per pixel by an AGSL shader (Android 13+).
 * On older Android it shows the plain [NovaOrb] instead.
 *
 * [amplitude] is the voice level, 0..1: it widens the silhouette and brightens the core.
 */
@Composable
fun LiaOrb3D(
    state: NovaOrbState,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    amplitude: Float = 0f,
    name: String = AssistantBrand.NAME,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        LiaOrbShader(state, modifier, size, amplitude, name)
    } else {
        NovaOrb(state = state, modifier = modifier, diameter = size, amplitude = amplitude, name = name)
    }
}

private class OrbPalette(
    val core: Color,
    val rim: Color,
    val halo: Color,
    val speed: Float,
    val breathPeriod: Float,
    val breathAmount: Float,
)

private fun paletteFor(state: NovaOrbState): OrbPalette = when (state) {
    NovaOrbState.IDLE -> OrbPalette(Color(0xFF9B6BFF), Color(0xFFFF8CC6), Color(0xFF7A4DFF), 0.5f, 4.2f, 0.35f)
    NovaOrbState.LISTENING -> OrbPalette(Color(0xFF3FE0D0), Color(0xFFA6FFF5), Color(0xFF1FB8A8), 0.7f, 2.8f, 1f)
    NovaOrbState.THINKING -> OrbPalette(Color(0xFF7C8CFF), Color(0xFFFF6FAE), Color(0xFF5B6CFF), 2.4f, 4.2f, 0.35f)
    NovaOrbState.SPEAKING -> OrbPalette(Color(0xFFFF8A6A), Color(0xFFFFC46B), Color(0xFFFF6FAE), 1.2f, 4.2f, 0.35f)
    NovaOrbState.CONNECTING -> OrbPalette(Color(0xFFFFB347), Color(0xFFFFE0A0), Color(0xFFFF9500), 0.35f, 4.2f, 0.35f)
    NovaOrbState.ERROR -> OrbPalette(Color(0xFF9A2E3C), Color(0xFFFF7A85), Color(0xFF6E1B28), 0.15f, 4.2f, 0.15f)
}

private const val TWO_PI = (2.0 * PI).toFloat()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LiaOrbShader(
    state: NovaOrbState,
    modifier: Modifier,
    size: Dp,
    amplitude: Float,
    name: String,
) {
    val reduced = NovaTheme.reducedMotion
    val pal = paletteFor(state)
    val core by animateColorAsState(pal.core, tween(NovaMotion.SLOW_MS), label = "orbCore")
    val rim by animateColorAsState(pal.rim, tween(NovaMotion.SLOW_MS), label = "orbRim")
    val halo by animateColorAsState(pal.halo, tween(NovaMotion.SLOW_MS), label = "orbHalo")
    val amp by animateFloatAsState(amplitude.coerceIn(0f, 1f), tween(90), label = "orbAmp")

    // phase: shader time, speed depends on the state. clock: real seconds, for the breathing.
    val speed = rememberUpdatedState(pal.speed)
    var phase by remember { mutableFloatStateOf(2f) }
    var clock by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        var last = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            clock += dt
            phase += dt * speed.value
        }
    }

    // Device tilt (low-pass filtered). Plain array: read every frame while drawing.
    val tilt = remember { FloatArray(2) }
    val context = LocalContext.current
    DisposableEffect(reduced) {
        val manager = if (reduced) null else context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val nx = (event.values[0] / 9.81f).coerceIn(-1f, 1f)
                val ny = ((event.values[1] / 9.81f - 0.7f) * 2f).coerceIn(-1f, 1f)
                tilt[0] += 0.08f * (nx - tilt[0])
                tilt[1] += 0.08f * (ny - tilt[1])
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (manager != null && sensor != null) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose { manager?.unregisterListener(listener) }
    }

    val shader = remember { RuntimeShader(ORB_AGSL) }
    val brush = remember(shader) { ShaderBrush(shader) }
    val description = "$name status: ${state.name.lowercase()}"
    val breathPeriod = pal.breathPeriod
    val breathAmount = pal.breathAmount

    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val breath = if (reduced) 0f else sin(clock * TWO_PI / breathPeriod) * breathAmount
        shader.setFloatUniform("iResolution", this.size.width, this.size.height)
        shader.setFloatUniform("iTime", phase)
        shader.setFloatUniform("iAmp", amp)
        shader.setFloatUniform("iBreath", breath)
        shader.setFloatUniform("iTilt", tilt[0], tilt[1])
        shader.setFloatUniform("iCore", core.red, core.green, core.blue)
        shader.setFloatUniform("iRim", rim.red, rim.green, rim.blue)
        shader.setFloatUniform("iHalo", halo.red, halo.green, halo.blue)
        drawRect(brush)
    }
}

private const val ORB_AGSL = """
uniform float2 iResolution;
uniform float iTime;
uniform float iAmp;
uniform float iBreath;
uniform float2 iTilt;
uniform float3 iCore;
uniform float3 iRim;
uniform float3 iHalo;

float hash3(float3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float noise3(float3 x) {
    float3 i = floor(x);
    float3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(hash3(i + float3(0.0, 0.0, 0.0)), hash3(i + float3(1.0, 0.0, 0.0)), f.x);
    float b = mix(hash3(i + float3(0.0, 1.0, 0.0)), hash3(i + float3(1.0, 1.0, 0.0)), f.x);
    float c = mix(hash3(i + float3(0.0, 0.0, 1.0)), hash3(i + float3(1.0, 0.0, 1.0)), f.x);
    float d = mix(hash3(i + float3(0.0, 1.0, 1.0)), hash3(i + float3(1.0, 1.0, 1.0)), f.x);
    return mix(mix(a, b, f.y), mix(c, d, f.y), f.z);
}

float fbm3(float3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise3(p);
        p = p * 2.02 + float3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return v;
}

float3 rotY(float3 p, float ang) {
    float c = cos(ang);
    float s = sin(ang);
    return float3(c * p.x + s * p.z, p.y, -s * p.x + c * p.z);
}

half4 main(float2 fragCoord) {
    float2 p = (fragCoord - 0.5 * iResolution) / min(iResolution.x, iResolution.y);
    float d = length(p);

    float wobble = 0.010 * iAmp * sin(atan(p.y, p.x) * 5.0 + iTime * 6.0);
    float R = (0.33 + 0.012 * iBreath) * (1.0 + 0.09 * iAmp) + wobble;

    // Soft halo outside the sphere.
    float h = exp(-max(d - R, 0.0) * 9.0) * (0.50 + 0.40 * iAmp);
    h *= 1.0 - smoothstep(0.40, 0.50, d);
    float haloA = clamp(h, 0.0, 1.0) * 0.9;

    // Sphere, per-pixel analytic normal.
    float2 q = p / R;
    float qq = dot(q, q);
    float z = sqrt(max(0.0, 1.0 - qq));
    float3 n = float3(q.x, -q.y, z);

    float3 L = normalize(float3(-0.45 + iTilt.x * 0.9, 0.55 + iTilt.y * 0.9, 0.75));
    float diff = max(dot(n, L), 0.0);
    float3 H = normalize(L + float3(0.0, 0.0, 1.0));
    float spec = pow(max(dot(n, H), 0.0), 48.0);
    float fres = pow(1.0 - n.z, 3.0);

    // Slowly rotating energy field inside the sphere.
    float3 sp = rotY(n, iTime * 0.35) * 1.9;
    float w = fbm3(sp + float3(0.0, iTime * 0.20, 0.0));
    float e = fbm3(sp + 1.6 * w + float3(0.0, 0.0, iTime * 0.15));
    e = smoothstep(0.25, 0.85, e);

    float3 base = mix(iCore * 0.30, iCore * 1.15, e);
    float3 col = base * (0.35 + 0.85 * diff);
    col += iCore * e * (0.25 + 0.90 * iAmp);
    col += iCore * exp(-qq * 3.0) * (0.15 + 0.50 * iAmp);
    col += iRim * fres * 1.2;
    col += float3(1.0) * spec * 0.55;
    col = clamp(col, 0.0, 1.0);

    float sa = 1.0 - smoothstep(R - 0.006, R, d);
    float3 outRGB = col * sa + iHalo * haloA * (1.0 - sa);
    float outA = sa + haloA * (1.0 - sa);
    return half4(half3(outRGB), half(outA));
}
"""
