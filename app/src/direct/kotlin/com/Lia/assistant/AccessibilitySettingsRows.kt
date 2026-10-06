package com.Lia.assistant

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun AccessibilitySettingsRows() {
    val ctx = LocalContext.current
    Button(onClick = { AccessKeyManager.openSettings(ctx) }) {
        Text(if (AccessKeyManager.isEnabled(ctx)) "Screen control: on" else "Enable screen control")
    }
}
