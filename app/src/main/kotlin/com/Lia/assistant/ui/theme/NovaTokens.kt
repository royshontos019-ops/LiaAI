package com.Lia.assistant.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 4 / 8 / 12 / 16 / 24 / 32 / 48 dp. */
object NovaSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

/** 12 / 20 / 28 dp and a pill. */
object NovaShapes {
    val small = RoundedCornerShape(12.dp)
    val medium = RoundedCornerShape(20.dp)
    val large = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(percent = 50)

    fun toMaterial() = Shapes(
        extraSmall = small,
        small = small,
        medium = medium,
        large = large,
        extraLarge = large,
    )
}

/** Animation lengths in milliseconds. */
object NovaMotion {
    const val FAST_MS = 150
    const val NORMAL_MS = 300
    const val SLOW_MS = 500
}

/** Smallest size of anything that can be tapped. */
val NovaMinTouchTarget = 48.dp
