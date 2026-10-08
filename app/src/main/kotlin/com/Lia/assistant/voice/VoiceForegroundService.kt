package com.Lia.assistant.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.Lia.assistant.MainActivity
import com.Lia.assistant.OverlayEdgeGlowController
import com.Lia.assistant.R
import com.Lia.assistant.data.AssistantBrand
import com.Lia.assistant.data.NovaPreferences
import com.Lia.assistant.ui.fx.EdgeGlowBus

/** Keeps the voice session alive (microphone foreground service) with a Stop action. */
class VoiceForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            VoiceSessionManager.stop()
            EdgeGlowBus.active.value = false
            OverlayEdgeGlowController.hide()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val name = assistantName()
        ensureChannel(name)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(name),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
        VoiceSessionManager.start(applicationContext)
        EdgeGlowBus.active.value = true
        OverlayEdgeGlowController.show(applicationContext)
        return START_STICKY
    }

    override fun onDestroy() {
        VoiceSessionManager.stop()
        EdgeGlowBus.active.value = false
        OverlayEdgeGlowController.hide()
        super.onDestroy()
    }

    private fun assistantName(): String =
        NovaPreferences.getString(this, NovaPreferences.Keys.ASSISTANT_NAME, AssistantBrand.NAME)
            .ifBlank { AssistantBrand.NAME }

    private fun ensureChannel(name: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "$name voice session", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun buildNotification(name: String): Notification {
        val immutable = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), immutable)
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, VoiceForegroundService::class.java).setAction(ACTION_STOP),
            immutable,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_voice)
            .setContentTitle("$name is listening")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(R.drawable.ic_stat_voice, "Stop", stop)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.Lia.assistant.action.STOP_VOICE"
        const val CHANNEL_ID = "lia_voice_session"
        const val NOTIFICATION_ID = 1001

        /** Starts the foreground service (and with it the voice session). */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, VoiceForegroundService::class.java))
        }

        /** Stops the session and the service. */
        fun stop(context: Context) {
            context.startService(Intent(context, VoiceForegroundService::class.java).setAction(ACTION_STOP))
        }
    }
}
