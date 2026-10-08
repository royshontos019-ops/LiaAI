package com.Lia.assistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.Lia.assistant.ui.theme.NovaShapes
import com.Lia.assistant.ui.theme.NovaSpacing
import com.Lia.assistant.ui.theme.NovaTheme
import com.Lia.assistant.ui.theme.depthSurface

/** A translucent panel with a light edge and a coloured shadow. */
@Composable
fun NovaGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = NovaShapes.medium,
    elevation: Dp = 8.dp,
    contentPadding: PaddingValues = PaddingValues(NovaSpacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = NovaTheme.colors
    Column(
        modifier = modifier
            .depthSurface(shape, elevation)
            .clip(shape)
            .background(colors.surfaceGlass)
            .border(1.dp, colors.surfaceBorder, shape)
            .padding(contentPadding),
        content = content,
    )
}
