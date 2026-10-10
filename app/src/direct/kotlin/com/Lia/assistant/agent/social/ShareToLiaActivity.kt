package com.Lia.assistant.agent.social

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * The "Share > Lia" target. It has no screen: it copies the photo or video into Lia's own folder,
 * remembers it, says so in a toast and closes. Lia can only post what was shared this way.
 */
class ShareToLiaActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = sharedUri(intent)
        if (intent?.action != Intent.ACTION_SEND || source == null) {
            finish()
            return
        }
        val app = applicationContext
        // The permission to read the file lasts only while this screen is open, so copy first, then close.
        Thread {
            val result = SharedMediaImport.import(app, source)
            runOnUiThread {
                val text = when (result) {
                    is SharedMediaImport.Result.Saved -> "Saved for Lia. Now ask Lia to post it."
                    is SharedMediaImport.Result.Rejected -> result.message
                }
                Toast.makeText(app, text, Toast.LENGTH_LONG).show()
                finish()
            }
        }.start()
    }

    private fun sharedUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
}

object SharedMediaImport {
    sealed interface Result {
        data class Saved(val uri: String) : Result
        data class Rejected(val message: String) : Result
    }

    private const val MAX_BYTES = 150L * 1024 * 1024
    private const val KEEP = 20

    fun import(context: Context, source: Uri): Result {
        val type = context.contentResolver.getType(source).orEmpty()
        val isVideo = type.startsWith("video/")
        if (!isVideo && !type.startsWith("image/")) return Result.Rejected("Lia can only use photos and videos.")

        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type) ?: if (isVideo) "mp4" else "jpg"
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val out = File(dir, "lia_${System.currentTimeMillis()}.$extension")

        try {
            val input = context.contentResolver.openInputStream(source)
                ?: return Result.Rejected("Could not read that file.")
            input.use { stream ->
                out.outputStream().use { sink ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) {
                            out.delete()
                            return Result.Rejected("That file is too big for Lia.")
                        }
                        sink.write(buffer, 0, n)
                    }
                }
            }
        } catch (e: Exception) {
            out.delete()
            return Result.Rejected("Could not copy that file.")
        }

        // Keep only the newest few files.
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(KEEP)?.forEach { it.delete() }

        val uri = FileProvider.getUriForFile(context, context.packageName + ".direct.shared", out).toString()
        SharedMediaStore.register(uri)
        return Result.Saved(uri)
    }
}
