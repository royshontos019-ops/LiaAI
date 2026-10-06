package com.Lia.assistant.action

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.net.URLEncoder

/**
 * Intents for calling and messaging. Nothing here sends anything by itself: SMS and WhatsApp
 * only open a composer with the text filled in. Never SmsManager, never SEND_SMS.
 */
object CommIntents {
    val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

    // ---- pure string builders (unit tested) ----
    fun digits(number: String): String = number.filter { it in '0'..'9' }

    fun telUri(number: String): String = "tel:" + ContactResolver.normalizeNumber(number)

    fun smsUri(number: String): String = "smsto:" + ContactResolver.normalizeNumber(number)

    /** wa.me wants digits only (international format), and %20 for spaces. */
    fun waUrl(number: String, text: String): String {
        val base = "https://wa.me/" + digits(number)
        if (text.isEmpty()) return base
        return base + "?text=" + URLEncoder.encode(text, "UTF-8").replace("+", "%20")
    }

    // ---- intents ----
    fun dial(number: String): Intent = Intent(Intent.ACTION_DIAL, Uri.parse(telUri(number)))

    fun call(number: String): Intent = Intent(Intent.ACTION_CALL, Uri.parse(telUri(number)))

    fun sms(number: String, body: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse(smsUri(number))).putExtra("sms_body", body)

    fun whatsapp(number: String, text: String, packageName: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(waUrl(number, text))).setPackage(packageName)

    /** First installed WhatsApp variant (regular, then Business), or null. */
    fun installedWhatsApp(pm: PackageManager): String? = WHATSAPP_PACKAGES.firstOrNull { isInstalled(pm, it) }

    private fun isInstalled(pm: PackageManager, packageName: String): Boolean = try {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
