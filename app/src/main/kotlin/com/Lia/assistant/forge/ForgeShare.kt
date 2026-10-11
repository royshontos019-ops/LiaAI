package com.Lia.assistant.forge

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** A FileProvider of its own, so a page can be shared without opening up any other folder. */
class ForgeFileProvider : FileProvider()

object ForgeShare {
    fun authority(context: Context): String = context.packageName + ".forge.fileprovider"

    fun uriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(context, authority(context), file)

    fun share(context: Context, file: File): Boolean = try {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/html")
            .putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share website").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    fun openInBrowser(context: Context, file: File): Boolean = try {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(context, file), "text/html")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(view)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: Exception) {
        false
    }

    /** Copies the page to a place the user chose (from the system "save as" screen). */
    fun copyTo(context: Context, file: File, destination: Uri): Boolean = try {
        context.contentResolver.openOutputStream(destination)?.use { out -> file.inputStream().use { it.copyTo(out) } } != null
    } catch (e: Exception) {
        false
    }

    fun delete(file: File): Boolean = try {
        file.delete()
    } catch (e: Exception) {
        false
    }
}
