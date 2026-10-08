package com.Lia.assistant.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import com.Lia.assistant.ui.theme.NovaMinTouchTarget
import com.Lia.assistant.ui.theme.NovaShapes
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.ui.theme.depthSurface

/** A labelled text field in the Nova look. Marigold border while it has focus. */
@Composable
fun NovaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    isPassword: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = NovaTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        enabled = enabled,
        singleLine = singleLine,
        isError = isError,
        shape = NovaShapes.small,
        textStyle = NovaTheme.type.body,
        label = label?.let { { Text(it, style = NovaTheme.type.label) } },
        placeholder = placeholder?.let { { Text(it, style = NovaTheme.type.body) } },
        supportingText = supportingText?.let { { Text(it, style = NovaTheme.type.caption) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            disabledTextColor = colors.textTertiary,
            errorTextColor = colors.textPrimary,
            focusedContainerColor = colors.surfaceGlass,
            unfocusedContainerColor = colors.surfaceGlass,
            disabledContainerColor = colors.surfaceGlass,
            errorContainerColor = colors.surfaceGlass,
            cursorColor = colors.accent,
            errorCursorColor = colors.error,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.surfaceBorder,
            disabledBorderColor = colors.surfaceBorder,
            errorBorderColor = colors.error,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.textSecondary,
            errorLabelColor = colors.error,
            focusedPlaceholderColor = colors.textTertiary,
            unfocusedPlaceholderColor = colors.textTertiary,
            focusedLeadingIconColor = colors.textSecondary,
            unfocusedLeadingIconColor = colors.textSecondary,
            focusedSupportingTextColor = colors.textSecondary,
            unfocusedSupportingTextColor = colors.textSecondary,
            errorSupportingTextColor = colors.error,
        ),
    )
}

/**
 * The floating "glass" message bar of the chat: a pill with a text field and a Send button.
 * Sends on the keyboard's Send key too.
 */
@Composable
fun NovaComposerBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    modifier: Modifier = Modifier,
    placeholder: String = "Message",
    sendContentDescription: String = "Send message",
) {
    val colors = NovaTheme.colors
    val shape = NovaShapes.large
    Row(
        modifier = modifier
            .fillMaxWidth()
            .depthSurface(shape, 10.dp)
            .clip(shape)
            .background(colors.surfaceGlass)
            .border(1.dp, colors.surfaceBorder, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).heightIn(min = NovaMinTouchTarget),
            placeholder = { Text(placeholder, style = NovaTheme.type.body) },
            textStyle = NovaTheme.type.body,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            colors = TextFieldDefaults.colors(
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = colors.accent,
                focusedPlaceholderColor = colors.textTertiary,
                unfocusedPlaceholderColor = colors.textTertiary,
            ),
        )
        NovaIconButton(
            icon = Icons.AutoMirrored.Filled.Send,
            contentDescription = sendContentDescription,
            onClick = onSend,
            enabled = canSend,
            tint = colors.accent,
        )
        Spacer(Modifier.width(NovaSpacing.xs))
    }
}
