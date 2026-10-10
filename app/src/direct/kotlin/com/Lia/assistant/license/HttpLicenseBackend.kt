package com.Lia.assistant.license

import com.Lia.assistant.BuildConfig
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Where the licence server is. The values come from app/licensing.json at build time (git-ignored,
 * like google-services.json). Without that file both are empty and licensing is "not configured".
 */
data class LicenseConfig(val baseUrl: String, val apiKey: String) {
    val isConfigured: Boolean get() = baseUrl.startsWith("https://") && baseUrl.length > "https://".length

    companion object {
        val current: LicenseConfig by lazy { LicenseConfig(BuildConfig.LICENSE_BASE_URL, BuildConfig.LICENSE_API_KEY) }
    }
}

/** Turns an HTTP answer into a [RemoteResult]. Anything unclear is "unreachable", which fails open. */
object LicenseResponse {
    fun parse(httpCode: Int, body: String): RemoteResult {
        if (httpCode !in 200..299) return RemoteResult.Unreachable("http $httpCode")
        val json = try {
            JSONObject(body)
        } catch (e: Exception) {
            return RemoteResult.Unreachable("unreadable answer")
        }
        return when (val status = json.optString("status")) {
            "ok" -> {
                val plan = json.optString("plan").trim().lowercase()
                if (plan.isEmpty()) RemoteResult.Unreachable("answer without a plan")
                else RemoteResult.Valid(plan, json.optLong("expiresAt", 0L))
            }
            "blocked" -> RemoteResult.Blocked
            "unknown_key" -> RemoteResult.Rejected(status, json.optString("message").ifBlank { "That key is not valid." })
            "device_mismatch" -> RemoteResult.Rejected(
                status,
                json.optString("message").ifBlank { "That key is already used on another device." },
            )
            else -> RemoteResult.Unreachable("unknown status")
        }
    }
}

class HttpLicenseBackend(
    private val config: LicenseConfig,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build(),
) : LicenseBackend {

    override val isConfigured: Boolean get() = config.isConfigured

    override suspend fun activate(key: String, deviceId: String): RemoteResult = call("activate", key, deviceId)

    override suspend fun check(key: String, deviceId: String): RemoteResult = call("check", key, deviceId)

    private suspend fun call(action: String, key: String, deviceId: String): RemoteResult = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext RemoteResult.NotConfigured
        try {
            val payload = JSONObject()
                .put("action", action)
                .put("key", key)
                .put("deviceId", deviceId)
                .put("app", "lia")
                .toString()
            val request = Request.Builder()
                .url(config.baseUrl)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .apply { if (config.apiKey.isNotBlank()) header("X-Api-Key", config.apiKey) }
                .build()
            client.newCall(request).execute().use { response ->
                LicenseResponse.parse(response.code, response.body?.string().orEmpty())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RemoteResult.Unreachable(e.javaClass.simpleName)
        }
    }
}
