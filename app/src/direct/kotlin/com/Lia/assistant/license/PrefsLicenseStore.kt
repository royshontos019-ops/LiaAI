package com.Lia.assistant.license

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

/** The licence state in private SharedPreferences "lia_access_key". Nothing here is sent anywhere by itself. */
class PrefsLicenseStore(context: Context) : LicenseStore {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): StoredLicense = StoredLicense(
        key = prefs.getString(KEY, "").orEmpty(),
        plan = prefs.getString(PLAN, "").orEmpty(),
        expiresAt = prefs.getLong(EXPIRES_AT, 0L),
        blocked = prefs.getBoolean(BLOCKED, false),
        lastRemoteCheck = prefs.getLong(LAST_REMOTE_CHECK, 0L),
    )

    override fun save(license: StoredLicense) {
        val d = AccessKeyRules.decide(
            license.plan, license.expiresAt, license.blocked, System.currentTimeMillis(), hasKey = license.key.isNotEmpty(),
        )
        prefs.edit()
            .putString(KEY, license.key)
            .putString(PLAN, license.plan)
            .putLong(EXPIRES_AT, license.expiresAt)
            .putBoolean(ACTIVE, d.active)
            .putBoolean(BLOCKED, license.blocked)
            .putLong(LAST_REMOTE_CHECK, license.lastRemoteCheck)
            .apply()
    }

    /** A hash of the phone's Android ID with this app's name, so the raw ID is never sent. */
    override fun deviceId(): String {
        val androidId = try {
            Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }
        if (!androidId.isNullOrBlank()) {
            val digest = MessageDigest.getInstance("SHA-256").digest("lia:${app.packageName}:$androidId".toByteArray())
            return digest.joinToString("") { "%02x".format(it) }.take(32)
        }
        prefs.getString(FALLBACK_DEVICE, null)?.let { return it }
        val fresh = UUID.randomUUID().toString().replace("-", "")
        prefs.edit().putString(FALLBACK_DEVICE, fresh).apply()
        return fresh
    }

    private companion object {
        const val PREFS = "lia_access_key"
        const val KEY = "key"
        const val PLAN = "plan"
        const val EXPIRES_AT = "expires_at"
        const val ACTIVE = "active"
        const val BLOCKED = "blocked"
        const val LAST_REMOTE_CHECK = "last_remote_check"
        const val FALLBACK_DEVICE = "device_id"
    }
}
