package com.Lia.assistant.agent.social

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens an app, optionally straight into its share screen with a file. */
interface AppOpener {
    suspend fun open(packageName: String, mediaUri: String?): Boolean
}

/** Production opener. The media URI is one of LIA's own FileProvider URIs, so it can be handed on. */
class AndroidAppOpener(private val context: Context) : AppOpener {

    override suspend fun open(packageName: String, mediaUri: String?): Boolean {
        return try {
            val intent = if (mediaUri == null) {
                context.packageManager.getLaunchIntentForPackage(packageName)
            } else {
                val uri = Uri.parse(mediaUri)
                val type = context.contentResolver.getType(uri) ?: "image/*"
                context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                Intent(Intent.ACTION_SEND).apply {
                    setPackage(packageName)
                    this.type = type
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            if (intent == null) return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
