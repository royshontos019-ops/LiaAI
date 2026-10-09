package com.Lia.assistant.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class StoredMessage(val isUser: Boolean, val text: String, val kind: String)

data class StoredChat(
    val id: String,
    val title: String,
    val updatedAtMillis: Long,
    val messages: List<StoredMessage>,
)

/** One line of the History list. */
data class ConversationSummary(
    val id: String,
    val title: String,
    val preview: String,
    val updatedAtMillis: Long,
)

/**
 * Chats are saved on this phone only, one small JSON file per conversation in the app's private
 * storage (filesDir/conversations). The user can switch saving off or delete everything in Privacy.
 */
object ConversationStore {
    private const val DIR = "conversations"
    private const val PREF_SAVE = "save_history"

    private val lock = Any()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isSavingEnabled(ctx: Context): Boolean = NovaPreferences.getBoolean(ctx, PREF_SAVE, true)

    fun setSavingEnabled(ctx: Context, enabled: Boolean) {
        NovaPreferences.putBoolean(ctx, PREF_SAVE, enabled)
    }

    fun newId(): String = "${System.currentTimeMillis()}-${(100..999).random()}"

    private fun dir(ctx: Context): File =
        File(ctx.applicationContext.filesDir, DIR).also { it.mkdirs() }

    private fun fileFor(ctx: Context, id: String): File {
        val safe = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        return File(dir(ctx), "$safe.json")
    }

    /** Saves in the background, so leaving the screen never loses the last message. */
    fun saveAsync(ctx: Context, chat: StoredChat) {
        val appContext = ctx.applicationContext
        ioScope.launch { save(appContext, chat) }
    }

    fun save(ctx: Context, chat: StoredChat) {
        if (chat.messages.isEmpty()) return
        val array = JSONArray()
        chat.messages.forEach { m ->
            array.put(JSONObject().put("u", m.isUser).put("t", m.text).put("k", m.kind))
        }
        val json = JSONObject()
            .put("id", chat.id)
            .put("title", chat.title)
            .put("updated", chat.updatedAtMillis)
            .put("messages", array)
        synchronized(lock) {
            val target = fileFor(ctx, chat.id)
            val temp = File(target.parentFile, target.name + ".tmp")
            temp.writeText(json.toString())
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        }
    }

    fun load(ctx: Context, id: String): StoredChat? {
        val file = fileFor(ctx, id)
        return try {
            val text = synchronized(lock) { if (file.exists()) file.readText() else null } ?: return null
            parse(text)
        } catch (e: Exception) {
            null
        }
    }

    /** Newest first. Files that cannot be read are skipped. */
    fun list(ctx: Context): List<ConversationSummary> {
        val files = dir(ctx).listFiles { f -> f.extension == "json" } ?: return emptyList()
        return files.mapNotNull { file ->
            try {
                val chat = parse(synchronized(lock) { file.readText() })
                if (chat.messages.isEmpty()) {
                    null
                } else {
                    ConversationSummary(
                        id = chat.id,
                        title = chat.title.ifBlank { "Conversation" },
                        preview = chat.messages.lastOrNull { it.kind != "FORGE" }
                            ?.text?.replace('\n', ' ')?.take(120).orEmpty(),
                        updatedAtMillis = chat.updatedAtMillis,
                    )
                }
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.updatedAtMillis }
    }

    fun delete(ctx: Context, id: String) {
        synchronized(lock) { fileFor(ctx, id).delete() }
    }

    fun deleteAll(ctx: Context) {
        synchronized(lock) { dir(ctx).listFiles()?.forEach { it.delete() } }
    }

    private fun parse(text: String): StoredChat {
        val root = JSONObject(text)
        val array = root.optJSONArray("messages") ?: JSONArray()
        val messages = (0 until array.length()).map { i ->
            val m = array.getJSONObject(i)
            StoredMessage(m.optBoolean("u"), m.optString("t"), m.optString("k", "NORMAL"))
        }
        return StoredChat(root.optString("id"), root.optString("title"), root.optLong("updated"), messages)
    }
}
