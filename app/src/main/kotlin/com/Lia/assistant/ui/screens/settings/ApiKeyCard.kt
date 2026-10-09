package com.Lia.assistant.ui.screens.settings

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.Lia.assistant.data.ApiKeyStore
import com.Lia.assistant.ui.components.NovaButton
import com.Lia.assistant.ui.components.NovaGlassCard
import com.Lia.assistant.ui.components.NovaSectionHeader
import com.Lia.assistant.ui.components.NovaTextField
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Where the Gemini API key is pasted. The key is hidden while typing, stored on the phone only,
 * and never logged.
 */
@Composable
fun ApiKeyCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 8.dp,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasKey by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { hasKey = withContext(Dispatchers.IO) { ApiKeyStore.hasKey(context) } }

    Column(modifier.fillMaxWidth()) {
        NovaSectionHeader("Gemini API key")
        NovaGlassCard(Modifier.fillMaxWidth(), elevation = elevation) {
            Text(
                if (hasKey) "Saved ✓" else "Not set yet",
                style = NovaTheme.type.body,
                color = if (hasKey) NovaTheme.colors.success else NovaTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(NovaSpacing.md))
            NovaTextField(
                value = field,
                onValueChange = { field = it },
                label = "Paste your Gemini API key",
                isPassword = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Spacer(Modifier.height(NovaSpacing.md))
            NovaButton(
                text = "Save key",
                enabled = field.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val key = field
                    field = ""
                    scope.launch {
                        withContext(Dispatchers.IO) { ApiKeyStore.saveKey(context, key) }
                        hasKey = withContext(Dispatchers.IO) { ApiKeyStore.hasKey(context) }
                    }
                },
            )
        }
    }
}
