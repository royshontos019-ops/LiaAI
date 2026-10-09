package com.Lia.assistant.ui.screens.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.Lia.assistant.data.NovaPreferences
import com.Lia.assistant.ui.components.NovaButton
import com.Lia.assistant.ui.components.NovaButtonStyle
import com.Lia.assistant.ui.components.NovaGlassCard
import com.Lia.assistant.ui.components.NovaTopBar
import com.Lia.assistant.ui.theme.NovaMotion
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.ui.theme.nightSky

/** Green = allowed, amber = not asked yet or can be asked again, red = blocked (needs Android Settings). */
enum class PermissionState { GRANTED, ASK, BLOCKED }

/**
 * Lists what Lia needs, each with a status light. The lights re-check every time you come back
 * to this screen (for example after changing something in Android Settings).
 * [extra] is a slot for flavour-specific rows (screen control in the direct build).
 */
@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable (refreshKey: Int) -> Unit = {},
) {
    val colors = NovaTheme.colors
    val context = LocalContext.current

    var refreshKey by remember { mutableIntStateOf(0) }
    val owner = remember(context) { context.findActivity() as? LifecycleOwner }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey++
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    Box(modifier.fillMaxSize().nightSky()) {
        Column(Modifier.fillMaxSize()) {
            NovaTopBar(title = "Permissions", onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = NovaSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(NovaSpacing.lg),
            ) {
                NovaGlassCard(Modifier.fillMaxWidth(), elevation = 8.dp) {
                    RuntimePermissionRow(
                        title = "Microphone",
                        subtitle = "So ${com.Lia.assistant.data.AssistantBrand.NAME} can hear you",
                        permission = Manifest.permission.RECORD_AUDIO,
                        refreshKey = refreshKey,
                        onChanged = { refreshKey++ },
                    )
                    if (Build.VERSION.SDK_INT >= 33) {
                        RuntimePermissionRow(
                            title = "Notifications",
                            subtitle = "Shows that a voice session is running",
                            permission = Manifest.permission.POST_NOTIFICATIONS,
                            refreshKey = refreshKey,
                            onChanged = { refreshKey++ },
                        )
                    }
                    RuntimePermissionRow(
                        title = "Contacts",
                        subtitle = "To find people by name",
                        permission = Manifest.permission.READ_CONTACTS,
                        refreshKey = refreshKey,
                        onChanged = { refreshKey++ },
                    )
                    RuntimePermissionRow(
                        title = "Phone calls",
                        subtitle = "To start a call for you",
                        permission = Manifest.permission.CALL_PHONE,
                        refreshKey = refreshKey,
                        onChanged = { refreshKey++ },
                    )
                    extra(refreshKey)
                }

                Text(
                    text = "You can change any of these in Android Settings too. This page checks again whenever you come back.",
                    style = NovaTheme.type.caption,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(NovaSpacing.xl))
            }
        }
    }
}

/** One row: status light, name and reason, and the one action that makes sense right now. */
@Composable
fun PermissionStatusRow(
    title: String,
    subtitle: String,
    state: PermissionState,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NovaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = NovaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusLight(state)
        Spacer(Modifier.width(NovaSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = NovaTheme.type.title, color = colors.textPrimary)
            Text(subtitle, style = NovaTheme.type.caption, color = colors.textSecondary)
        }
        if (actionLabel != null) {
            Spacer(Modifier.width(NovaSpacing.sm))
            NovaButton(text = actionLabel, onClick = onAction, style = NovaButtonStyle.TEXT)
        } else {
            Text("Allowed", style = NovaTheme.type.label, color = colors.textTertiary)
        }
    }
}

/** A small light that pulses once whenever its state changes. */
@Composable
private fun StatusLight(state: PermissionState) {
    val colors = NovaTheme.colors
    val reduced = NovaTheme.reducedMotion
    val target = when (state) {
        PermissionState.GRANTED -> colors.lagoon
        PermissionState.ASK -> colors.warning
        PermissionState.BLOCKED -> colors.error
    }
    val color by animateColorAsState(target, tween(NovaMotion.NORMAL_MS), label = "statusLight")
    val pulse = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }

    LaunchedEffect(state) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (!reduced) {
            pulse.snapTo(1.9f)
            pulse.animateTo(1f, tween(600))
        }
    }

    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(12.dp)
                .graphicsLayer {
                    scaleX = pulse.value
                    scaleY = pulse.value
                }
                .drawBehind { drawCircle(color.copy(alpha = 0.35f), radius = this.size.minDimension) }
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
private fun RuntimePermissionRow(
    title: String,
    subtitle: String,
    permission: String,
    refreshKey: Int,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val askedKey = "perm_asked_$permission"

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        NovaPreferences.putBoolean(context, askedKey, true)
        onChanged()
    }
    val state = remember(refreshKey) { permissionState(context, activity, permission, askedKey) }

    PermissionStatusRow(
        title = title,
        subtitle = subtitle,
        state = state,
        actionLabel = when (state) {
            PermissionState.GRANTED -> null
            PermissionState.ASK -> "Allow"
            PermissionState.BLOCKED -> "Open Settings"
        },
        onAction = {
            when (state) {
                PermissionState.ASK -> launcher.launch(permission)
                PermissionState.BLOCKED -> openAppSettings(context)
                PermissionState.GRANTED -> Unit
            }
        },
    )
}

private fun permissionState(
    context: Context,
    activity: Activity?,
    permission: String,
    askedKey: String,
): PermissionState {
    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
        return PermissionState.GRANTED
    }
    val asked = NovaPreferences.getBoolean(context, askedKey, false)
    val canAskAgain = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    return if (asked && !canAskAgain) PermissionState.BLOCKED else PermissionState.ASK
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
