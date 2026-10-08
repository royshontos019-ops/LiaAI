package com.Lia.assistant

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.Lia.assistant.ui.fx.EdgeGlowBus
import com.Lia.assistant.ui.fx.ListeningEdgeGlow
import com.Lia.assistant.ui.theme.NovaTheme

/**
 * Shows the listening edge glow in a system overlay window, so it stays visible over other apps.
 * It needs the "display over other apps" permission and quietly does nothing without it.
 * While Lia's own screen is in front, the in-app dialog draws the glow and this window stays empty.
 */
object OverlayEdgeGlowController {
    private val main = Handler(Looper.getMainLooper())
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Opens the system screen where the person can allow the overlay. */
    fun permissionIntent(context: Context): Intent? =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun show(context: Context) {
        val app = context.applicationContext
        main.post { showNow(app) }
    }

    fun hide() {
        main.post { hideNow() }
    }

    private fun showNow(context: Context) {
        if (view != null || !canShow(context)) return
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val newOwner = OverlayOwner().also { it.start() }
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(newOwner)
            setViewTreeViewModelStoreOwner(newOwner)
            setViewTreeSavedStateRegistryOwner(newOwner)
            setContent {
                NovaTheme {
                    val active by EdgeGlowBus.active.collectAsState()
                    val appVisible by EdgeGlowBus.appVisible.collectAsState()
                    val mode by EdgeGlowBus.mode.collectAsState()
                    if (active && !appVisible) {
                        ListeningEdgeGlow(mode = mode, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        try {
            windowManager.addView(composeView, params)
            view = composeView
            owner = newOwner
        } catch (_: Exception) {
            newOwner.stop()
        }
    }

    private fun hideNow() {
        val current = view ?: return
        val windowManager = current.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        try {
            windowManager?.removeViewImmediate(current)
        } catch (_: Exception) {
        }
        owner?.stop()
        view = null
        owner = null
    }
}

/** A ComposeView outside an Activity needs its own lifecycle, saved-state and view-model owners. */
private class OverlayOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    fun start() {
        controller.performAttach()
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
