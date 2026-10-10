package com.Lia.assistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.Lia.assistant.data.AssistantBrand
import com.Lia.assistant.ui.screens.permissions.PermissionState
import com.Lia.assistant.ui.screens.permissions.PermissionStatusRow

/** Direct flavor: the screen-control switch, shown with a status light on the Permissions screen. */
@Composable
fun AccessibilityPermissionRow(refreshKey: Int) {
    val context = LocalContext.current
    val enabled = remember(refreshKey) { ScreenControlAccess.isEnabled(context) }
    PermissionStatusRow(
        title = "Screen control",
        subtitle = "Lets ${AssistantBrand.NAME} read and tap things on your screen",
        state = if (enabled) PermissionState.GRANTED else PermissionState.ASK,
        actionLabel = if (enabled) null else "Open Settings",
        onAction = { ScreenControlAccess.openSettings(context) },
    )
}
