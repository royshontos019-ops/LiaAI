package com.Lia.assistant

import android.content.Context
import android.content.Intent

/** Play build: no system overlay. Same signature as the direct build, does nothing. */
@Suppress("UNUSED_PARAMETER")
object OverlayEdgeGlowController {
    fun canShow(context: Context): Boolean = false
    fun permissionIntent(context: Context): Intent? = null
    fun show(context: Context) = Unit
    fun hide() = Unit
}
