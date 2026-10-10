package com.Lia.assistant.forge

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.Lia.assistant.MainActivity
import com.Lia.assistant.data.ApiKeyStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

/** Finished pages live in filesDir/Websites. */
class FileSiteStore(private val context: () -> Context?) : SiteStore {
    private fun dir(): File? = context()?.let { File(it.filesDir, "Websites").apply { mkdirs() } }

    override fun save(html: String): File? = try {
        dir()?.let { d -> File(d, "website_${System.currentTimeMillis()}.html").also { it.writeText(html) } }
    } catch (e: Exception) {
        null
    }

    override fun latest(): File? = list().firstOrNull()

    override fun read(file: File): String? = try {
        file.readText()
    } catch (e: Exception) {
        null
    }

    /** Newest first. */
    fun list(): List<File> =
        dir()?.listFiles { f -> f.isFile && f.name.startsWith("website_") && f.name.endsWith(".html") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
}

object ForgeNotifier {
    const val EXTRA_OPEN_FORGE = "com.Lia.assistant.OPEN_FORGE"
    private const val CHANNEL = "forge_ready"
    private const val ID = 1401

    @SuppressLint("MissingPermission")
    fun postReady(context: Context) {
        try {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            if (Build.VERSION.SDK_INT >= 26) {
                val channel = NotificationChannel(CHANNEL, "Website ready", NotificationManager.IMPORTANCE_DEFAULT)
                context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
            }
            val open = Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_FORGE, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(
                context, ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Your website is ready")
                .setContentText("Tap to open it in Lia.")
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            manager.notify(ID, notification)
        } catch (e: SecurityException) {
            // Notifications are off for this app: nothing to do.
        }
    }
}

/** The one Website Forge of this process. The screen, the voice tool and the chat all talk to this. */
object ForgeController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var appContext: Context? = null

    /** The Forge screen sets this while it is on screen, so no notification is posted then. */
    @Volatile var screenVisible: Boolean = false

    val store = FileSiteStore { appContext }

    private val environment = object : ForgeEnvironment {
        override fun apiKey(): String = appContext?.let { ApiKeyStore.getKey(it) }.orEmpty()

        override fun isOnline(): Boolean {
            val ctx = appContext ?: return false
            return try {
                val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return true
                val network = cm.activeNetwork ?: return false
                cm.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            } catch (e: SecurityException) {
                true // no permission to ask: assume yes and let the request decide
            }
        }

        override fun onFinished(state: ForgeUiState) {
            val ctx = appContext ?: return
            if (state.phase == ForgePhase.DONE && !screenVisible) ForgeNotifier.postReady(ctx)
        }
    }

    private val session = ForgeSession(scope, WebsiteForgeClient(), store, environment)

    val state: StateFlow<ForgeUiState> get() = session.state
    val pendingOpen: StateFlow<Boolean> get() = session.pendingOpen

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    fun start(context: Context, prompt: String): StartResult {
        attach(context)
        return session.start(prompt)
    }

    fun edit(context: Context, change: String): StartResult {
        attach(context)
        return session.edit(change)
    }

    fun retry(context: Context): StartResult {
        attach(context)
        return session.retry()
    }

    fun cancel() = session.cancel()

    fun reset() = session.reset()

    fun consumeOpen() = session.consumeOpen()

    fun openSaved(file: File) = session.openSaved(file)

    fun partialHtml(): String = session.partialHtml()
}
