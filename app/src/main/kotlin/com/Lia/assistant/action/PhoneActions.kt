package com.Lia.assistant.action

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The three actions both flavors share. Messaging only opens a composer; the direct flavor may
 * then tap Send through the accessibility service ([MessageOutcome] carries what it needs).
 */
object PhoneActions {
    class MessageOutcome(
        val json: JSONObject,
        val composerOpened: Boolean,
        val targetPackage: String? = null,
        val isWhatsApp: Boolean = false,
    )

    suspend fun openApp(context: Context, args: JSONObject): JSONObject {
        val query = args.optString("app_name").trim()
        if (query.isEmpty()) return ToolJson.missing("app_name")
        val match = AppLauncher.findApp(InstalledAppLabelCache.get(context), query)
            ?: return ToolJson.result("app_not_found")
        return if (AppLauncher.launch(context, match.packageName)) {
            ToolJson.result("opened", "app" to match.label)
        } else {
            ToolJson.result("launch_failed", "app" to match.label)
        }
    }

    suspend fun callContact(context: Context, args: JSONObject): JSONObject {
        val query = args.optString("contact").trim()
        if (query.isEmpty()) return ToolJson.missing("contact")
        return when (val found = withContext(Dispatchers.IO) { ContactResolver.lookup(context, query) }) {
            ContactLookup.NoPermission -> ToolJson.result("no_contacts_permission")
            ContactLookup.NotFound -> ToolJson.result("contact_not_found")
            is ContactLookup.Found -> startCall(context, found)
        }
    }

    private fun startCall(context: Context, who: ContactLookup.Found): JSONObject {
        val canCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val intent = (if (canCall) CommIntents.call(who.number) else CommIntents.dial(who.number))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            ToolJson.result(
                if (canCall) "calling" else "dialer_opened_no_permission",
                "contact" to who.displayName,
            )
        } catch (_: ActivityNotFoundException) {
            ToolJson.result("call_failed")
        } catch (_: SecurityException) {
            ToolJson.result("call_failed")
        }
    }

    suspend fun messageContact(context: Context, args: JSONObject): MessageOutcome {
        val query = args.optString("contact").trim()
        val text = args.optString("message").trim()
        if (query.isEmpty()) return fail(ToolJson.missing("contact"))
        if (text.isEmpty()) return fail(ToolJson.missing("message"))
        val channel = args.optString("app").trim().lowercase()

        val who = when (val found = withContext(Dispatchers.IO) { ContactResolver.lookup(context, query) }) {
            ContactLookup.NoPermission -> return fail(ToolJson.result("no_contacts_permission"))
            ContactLookup.NotFound -> return fail(ToolJson.result("contact_not_found"))
            is ContactLookup.Found -> found
        }
        return if (channel == "whatsapp" || channel == "wa") {
            openWhatsApp(context, who, text)
        } else {
            openSms(context, who, text)
        }
    }

    private fun fail(json: JSONObject) = MessageOutcome(json, composerOpened = false)

    private fun openSms(context: Context, who: ContactLookup.Found, text: String): MessageOutcome {
        val intent = CommIntents.sms(who.number, text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val target = intent.resolveActivity(context.packageManager)?.packageName
        return try {
            context.startActivity(intent)
            MessageOutcome(ToolJson.result("composer_opened"), true, target, isWhatsApp = false)
        } catch (_: ActivityNotFoundException) {
            fail(ToolJson.result("no_sms_app"))
        } catch (_: SecurityException) {
            fail(ToolJson.result("composer_failed"))
        }
    }

    private fun openWhatsApp(context: Context, who: ContactLookup.Found, text: String): MessageOutcome {
        val pkg = CommIntents.installedWhatsApp(context.packageManager)
            ?: return fail(ToolJson.result("whatsapp_not_installed"))
        val intent = CommIntents.whatsapp(who.number, text, pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            MessageOutcome(ToolJson.result("composer_opened"), true, pkg, isWhatsApp = true)
        } catch (_: ActivityNotFoundException) {
            fail(ToolJson.result("whatsapp_not_installed"))
        } catch (_: SecurityException) {
            fail(ToolJson.result("composer_failed"))
        }
    }
}
