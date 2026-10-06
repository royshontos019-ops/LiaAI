package com.Lia.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Launcher-app label -> package name, loaded once on a background thread. */
object InstalledAppLabelCache {
    private val byLabel = ConcurrentHashMap<String, String>()
    private val started = AtomicBoolean(false)

    val size: Int get() = byLabel.size

    /** Starts loading in the background (no-op if already started). Safe on the main thread. */
    fun prewarm(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        Thread({
            try {
                load(app)
            } catch (_: Exception) {
                started.set(false) // allow a later retry
            }
        }, "Lia-AppLabels").start()
    }

    /** Forget everything (e.g. after an app was installed); the next prewarm reloads. */
    fun invalidate() {
        started.set(false)
        byLabel.clear()
    }

    fun packageFor(label: String): String? {
        val q = label.trim().lowercase()
        if (q.isEmpty()) return null
        byLabel[q]?.let { return it }
        return byLabel.entries.firstOrNull { it.key.contains(q) }?.value
    }

    private fun load(app: Context) {
        val pm = app.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val apps = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentActivities(intent, 0)
        }
        for (info in apps) {
            val label = info.loadLabel(pm)?.toString()?.trim()?.lowercase().orEmpty()
            if (label.isNotEmpty()) byLabel.putIfAbsent(label, info.activityInfo.packageName)
        }
    }
}
