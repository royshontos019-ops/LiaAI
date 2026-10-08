package com.Lia.assistant

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.Lia.assistant.ui.components.NovaButton
import com.Lia.assistant.ui.components.NovaButtonStyle

@Composable
fun AccessibilitySettingsRows() {
    val ctx = LocalContext.current
    NovaButton(
        text = if (AccessKeyManager.isEnabled(ctx)) "Screen control: on" else "Enable screen control",
        onClick = { AccessKeyManager.openSettings(ctx) },
        modifier = Modifier.fillMaxWidth(),
        style = NovaButtonStyle.SECONDARY,
    )
}
