package com.Lia.assistant.voice

import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Typed chat over the Gemini REST API. The model id is never hardcoded: it is looked up from the
 * account's model list once per process. The API key travels in a header, is never logged and is
 * never part of an error message; user text is never logged.
 */
object GeminiTextClient {
    private const val TAG = "GeminiTextClient"
    private const val KEY_HEADER = "x-goog-api-key"
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    /** Shared with the Forge. */
    internal val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    @Volatile private var cachedModel: String? = null

    private class HttpResult(val code: Int, val body: String)

    private sealed interface Resolution {
        data class Model(val name: String) : Resolution
        data class Failure(val message: String) : Resolution
    }

    /** The chat model to use ("models/..."), or null if it cannot be determined. Shared with the Forge. */
    internal suspend fun resolveModel(apiKey: String): String? =
        (resolve(apiKey.trim()) as? Resolution.Model)?.name

    suspend fun reply(apiKey: String, systemInstruction: String, history: List<ChatTurn>): ChatReplyResult {
        val key = apiKey.trim()
        if (key.isEmpty()) return ChatReplyResult.Error("Add your Gemini API key first, then try again.")
        val turns = GeminiTextProtocol.prepareHistory(history)
        if (turns.isEmpty()) return ChatReplyResult.Error("There's nothing to reply to yet.")

        return try {
            val model = when (val r = resolve(key)) {
                is Resolution.Model -> r.name
                is Resolution.Failure -> return ChatReplyResult.Error(r.message)
            }
            val request = Request.Builder()
                .url("${GeminiTextProtocol.BASE_URL}/${GeminiTextProtocol.modelPath(model)}:generateContent")
                .header(KEY_HEADER, key)
                .post(GeminiTextProtocol.buildRequest(systemInstruction, turns).toRequestBody(JSON_TYPE))
                .build()
            val result = withContext(Dispatchers.IO) { execute(request) }

            if (result.code !in 200..299) {
                val reason = GeminiTextProtocol.errorReason(result.body)
                logHttp(result.code, reason, key)
                if (result.code == 404) cachedModel = null // pick again next time
                return ChatReplyResult.Error(GeminiTextProtocol.httpMessage(result.code, reason, key))
            }
            when (val parsed = GeminiTextProtocol.parseReply(result.body)) {
                is ParsedReply.Text -> ChatReplyResult.Success(parsed.text)
                is ParsedReply.Blocked ->
                    ChatReplyResult.Error("Gemini didn't answer that (${parsed.reason}). Try rephrasing your message.")
                ParsedReply.Empty -> ChatReplyResult.Error("Gemini sent back an empty reply. Please try again.")
            }
        } catch (_: IOException) {
            ChatReplyResult.Error("Couldn't reach Gemini. Check your internet connection and try again.")
        }
    }

    private suspend fun resolve(apiKey: String): Resolution {
        cachedModel?.let { return Resolution.Model(it) }
        if (apiKey.isEmpty()) return Resolution.Failure("Add your Gemini API key first, then try again.")
        return try {
            val request = Request.Builder()
                .url("${GeminiTextProtocol.BASE_URL}/models?pageSize=1000")
                .header(KEY_HEADER, apiKey)
                .get()
                .build()
            val result = withContext(Dispatchers.IO) { execute(request) }
            if (result.code !in 200..299) {
                val reason = GeminiTextProtocol.errorReason(result.body)
                logHttp(result.code, reason, apiKey)
                return Resolution.Failure(GeminiTextProtocol.httpMessage(result.code, reason, apiKey))
            }
            val name = GeminiTextProtocol.pickModel(GeminiTextProtocol.parseModels(result.body))
                ?: return Resolution.Failure("Gemini has no chat model available for this key.")
            cachedModel = name
            Resolution.Model(name)
        } catch (_: IOException) {
            Resolution.Failure("Couldn't reach Gemini. Check your internet connection and try again.")
        }
    }

    private suspend fun execute(request: Request): HttpResult =
        http.newCall(request).await().use { r -> HttpResult(r.code, r.body?.string().orEmpty()) }

    /** Status code and Gemini's own reason only: no key, no request text. */
    private fun logHttp(code: Int, reason: String?, apiKey: String) {
        Log.w(TAG, "HTTP $code: ${LiveErrors.sanitize(reason, apiKey, 300)}")
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }

            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        })
    }
}
