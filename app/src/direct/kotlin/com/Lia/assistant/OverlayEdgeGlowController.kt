package com.Lia.assistant

import android.content.Context
import android.provider.Settings

/** Edge-glow overlay shown while a visual agent is acting. Skeleton: window drawing comes later. */
object OverlayEdgeGlowController {
    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)
    fun show(context: Context) { if (canShow(context)) { /* TODO: add overlay view via WindowManager */ } }
    fun hide() { /* TODO: remove overlay view */ }
}
