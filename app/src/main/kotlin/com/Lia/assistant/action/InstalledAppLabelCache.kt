package com.Lia.assistant.action

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Process-wide map of lowercase app label -> package name for launchable apps.
 * Built once on Dispatchers.IO under a mutex, refreshed after [TTL_MS] (15 minutes).
 * Package visibility comes from the launcher <queries> entry in the manifest, so this works
 * in both flavors without QUERY_ALL_PACKAGES.
 */
object InstalledAppLabelCache {
    const val TTL_MS = 15 * 60 * 1000L

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var labels: Map<String, String> = emptyMap()
    @Volatile private var loadedAt = -1L

    internal fun isFresh(loadedAt: Long, now: Long, ttl: Long = TTL_MS): Boolean =
        loadedAt >= 0 && now - loadedAt < ttl

    /** Fire-and-forget: starts loading in the background. Safe on the main thread. */
    fun prewarm(context: Context) {
        val app = context.applicationContext
        scope.launch { get(app) }
    }

    /** Forces a reload on the next [get]. */
    fun invalidate() {
        loadedAt = -1L
    }

    suspend fun get(context: Context): Map<String, String> {
        if (isFresh(loadedAt, SystemClock.elapsedRealtime())) return labels
        return mutex.withLock {
            if (isFresh(loadedAt, SystemClock.elapsedRealtime())) return@withLock labels
            val built = withContext(Dispatchers.IO) { load(context.applicationContext) }
            if (built.isNotEmpty()) {
                labels = built
                loadedAt = SystemClock.elapsedRealtime()
            }
            built
        }
    }

    private fun load(app: Context): Map<String, String> {
        val pm = app.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val infos = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            pm.queryIntentActivities(intent, 0)
        }
        val out = LinkedHashMap<String, String>()
        for (info in infos) {
            val label = info.loadLabel(pm)?.toString()?.trim()?.lowercase().orEmpty()
            if (label.isNotEmpty()) out.putIfAbsent(label, info.activityInfo.packageName)
        }
        return out
    }
}
