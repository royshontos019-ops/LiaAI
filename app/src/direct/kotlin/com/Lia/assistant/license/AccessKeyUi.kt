package com.Lia.assistant.license

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Lia.assistant.ui.components.NovaButton
import com.Lia.assistant.ui.components.NovaGlassCard
import com.Lia.assistant.ui.components.NovaTextField
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import kotlinx.coroutines.launch

/** Home: shown only when something needs attention (not active, ending soon, or not configured). */
@Composable
fun AccessKeyBanner(onOpenProfile: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val info by AccessKeyManager.info.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { AccessKeyManager.refreshInfo(context) }

    val soon = info.active && AccessKeyRules.expiringSoon(info.plan, info.expiresAt, System.currentTimeMillis())
    val unconfigured = !LicenseConfig.current.isConfigured
    if (info.active && !soon && !unconfigured) return

    val colors = NovaTheme.colors
    val text = when {
        unconfigured -> "Licensing not configured"
        !info.active -> "${info.statusText}. Tap to enter an access key."
        else -> "${info.statusText}. Tap to renew."
    }
    NovaGlassCard(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Open Profile", onClick = onOpenProfile),
        elevation = 8.dp,
    ) {
        Text(
            text = text,
            style = NovaTheme.type.body,
            color = if (!info.active && !unconfigured) colors.error else colors.textPrimary,
        )
    }
}

/** Profile: the status, and a box to enter or change the access key. */
@Composable
fun AccessKeySection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val info by AccessKeyManager.info.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { AccessKeyManager.refreshInfo(context) }

    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val colors = NovaTheme.colors
    val configured = LicenseConfig.current.isConfigured

    NovaGlassCard(modifier.fillMaxWidth(), elevation = 8.dp) {
        Column {
            Text(text = "Access key", style = NovaTheme.type.title, color = colors.textPrimary)
            Spacer(Modifier.height(NovaSpacing.sm))
            Text(
                text = if (configured) info.statusText.ifBlank { "Checking\u2026" } else "Licensing not configured",
                style = NovaTheme.type.body,
                color = if (configured && !info.active) colors.error else colors.textSecondary,
            )
            if (configured) {
                Spacer(Modifier.height(NovaSpacing.md))
                NovaTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = if (info.hasRememberedKey) "New access key (optional)" else "Access key",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                Spacer(Modifier.height(NovaSpacing.md))
                NovaButton(
                    text = if (busy) "Checking\u2026" else "Activate",
                    onClick = {
                        if (busy || key.isBlank()) return@NovaButton
                        busy = true
                        message = null
                        scope.launch {
                            message = when (val r = AccessKeyManager.activate(context, key)) {
                                is ActivationResult.Activated -> {
                                    key = ""
                                    "Activated."
                                }
                                is ActivationResult.Rejected -> r.message
                                is ActivationResult.NetworkError -> r.message
                                is ActivationResult.InvalidFormat -> r.message
                                ActivationResult.NotConfigured -> "Licensing not configured"
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                message?.let {
                    Spacer(Modifier.height(NovaSpacing.sm))
                    Text(text = it, style = NovaTheme.type.caption, color = colors.textSecondary)
                }
            }
        }
    }
}
