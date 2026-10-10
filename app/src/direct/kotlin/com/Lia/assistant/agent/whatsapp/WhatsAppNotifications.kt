package com.Lia.assistant.agent.whatsapp

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** What the listener has seen: WhatsApp notifications that are still showing. */
object WhatsAppNotificationStore : UnreadMessageSource {
    private const val MAX = 50
    private val chats = LinkedHashMap<String, UnreadChat>()

    @Volatile
    var listenerConnected: Boolean = false

    override val isAvailable: Boolean get() = listenerConnected

    @Synchronized
    fun post(key: String, chat: UnreadChat) {
        chats.remove(key)
        chats[key] = chat
        while (chats.size > MAX) chats.remove(chats.keys.first())
    }

    @Synchronized
    fun remove(key: String) {
        chats.remove(key)
    }

    @Synchronized
    fun clear() = chats.clear()

    /** Newest first. */
    @Synchronized
    override fun unread(): List<UnreadChat> = chats.values.toList().asReversed()
}

/** Collects WhatsApp notifications. It reads only the title and text WhatsApp put in them. */
class WhatsAppNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        WhatsAppNotificationStore.listenerConnected = true
        try {
            activeNotifications?.forEach { handle(it) }
        } catch (e: Exception) {
        }
    }

    override fun onListenerDisconnected() {
        WhatsAppNotificationStore.listenerConnected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) = WhatsAppNotificationStore.remove(sbn.key)

    private fun handle(sbn: StatusBarNotification) {
        if (sbn.packageName != WhatsAppSelectors.PACKAGE && sbn.packageName != "com.whatsapp.w4b") return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isEmpty() || text.isEmpty()) return
        val count = Regex("^(\\d+) new messages?").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        WhatsAppNotificationStore.post(sbn.key, UnreadChat(title, text, count))
    }
}

class AndroidWhatsAppLauncher(private val context: Context) : WhatsAppLauncher {

    override suspend fun openChat(phone: String, prefill: String?): Boolean {
        val url = buildString {
            append("https://wa.me/").append(phone)
            if (!prefill.isNullOrEmpty()) append("?text=").append(Uri.encode(prefill))
        }
        return start(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(WhatsAppSelectors.PACKAGE),
        )
    }

    override suspend fun openApp(): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(WhatsAppSelectors.PACKAGE) ?: return false
        return start(intent)
    }

    private fun start(intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}
