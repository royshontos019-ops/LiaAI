package com.Lia.assistant.license

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * The access-key gate for the direct build. When licensing is not configured (no app/licensing.json
 * at build time) nothing is enforced and [info] says "Licensing not configured".
 */
object AccessKeyManager {
    private val _info = MutableStateFlow(LicenseInfo.UNKNOWN)
    val info: StateFlow<LicenseInfo> = _info.asStateFlow()

    /** Tests put a ready-made engine here (and null again afterwards). */
    @Volatile internal var engine: LicenseEngine? = null

    /** Tests set this to pretend licensing is (not) configured. Null means "ask the build settings". */
    @Volatile internal var configuredOverride: Boolean? = null

    private val configured: Boolean get() = configuredOverride ?: LicenseConfig.current.isConfigured

    private fun engine(context: Context): LicenseEngine =
        engine ?: synchronized(this) {
            engine ?: LicenseEngine(
                PrefsLicenseStore(context.applicationContext),
                HttpLicenseBackend(LicenseConfig.current),
            ).also { engine = it }
        }

    /** Loads the local state into [info]. No network. */
    fun refreshInfo(context: Context) {
        _info.value = if (configured) engine(context).info() else LicenseInfo.NOT_CONFIGURED
    }

    /** True when the key is active, or when licensing is not configured. Never touches the network. */
    fun isActive(context: Context): Boolean = !configured || engine(context).isActive()

    suspend fun activate(context: Context, key: String): ActivationResult {
        if (!configured) return ActivationResult.NotConfigured
        val result = engine(context).activate(key)
        refreshInfo(context)
        return result
    }

    /** At most once per 3 hours. A network problem never locks anyone out. */
    suspend fun liveCheck(context: Context, force: Boolean = false): LiveCheckResult {
        if (!configured) return LiveCheckResult.NotConfigured
        val result = engine(context).liveCheck(force)
        refreshInfo(context)
        return result
    }

    /** A sentence the model can say to the user. */
    fun inactiveMessage(context: Context): String {
        val status = if (configured) engine(context).info().statusText else ""
        val detail = if (status.isNotBlank()) " ($status)" else ""
        return "Your Lia access key is not active$detail. Ask the user to open Profile and enter a valid access key."
    }

    /** What a phone tool returns while the key is not active. */
    fun inactiveReply(context: Context): JSONObject =
        JSONObject().put("result", "access_key_inactive").put("error", inactiveMessage(context))
}
