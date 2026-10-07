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
 * Typed chat over the Gemini REST API. No model id is hardcoded: the account's model list is
 * fetched once per process and the first model that actually answers is remembered. The API key
 * travels in a header, is never logged and is never part of an error message; user text is
 * never logged.
 */
object GeminiTextClient {
    private const val TAG = "GeminiTextClient"
    private const val KEY_HEADER = "x-goog-api-key"
    private const val MAX_MODEL_ATTEMPTS = 6
    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    /** Shared with the Forge. */
    internal val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    @Volatile private var candidates: List<String>? = null
    @Volatile private var workingModel: String? = null

    private class HttpResult(val code: Int, val body: String)

    private sealed interface Resolution {
        data class Models(val names: List<String>) : Resolution
        data class Failure(val message: String) : Resolution
    }

    /**
     * The chat model to use ("models/..."), or null if none can be determined. Once a chat call
     * has succeeded this is the model that worked; before that it is the first candidate.
     * Shared with the Forge.
     */
    internal suspend fun resolveModel(apiKey: String): String? {
        workingModel?.let { return it }
        return (resolve(apiKey.trim()) as? Resolution.Models)?.names?.firstOrNull()
    }

    suspend fun reply(apiKey: String, systemInstruction: String, history: List<ChatTurn>): ChatReplyResult {
        val key = apiKey.trim()
        if (key.isEmpty()) return ChatReplyResult.Error("Add your Gemini API key first, then try again.")
        val turns = GeminiTextProtocol.prepareHistory(history)
        if (turns.isEmpty()) return ChatReplyResult.Error("There's nothing to reply to yet.")
        val requestBody = GeminiTextProtocol.buildRequest(systemInstruction, turns)

        return try {
            val names = when (val r = resolve(key)) {
                is Resolution.Models -> r.names
                is Resolution.Failure -> return ChatReplyResult.Error(r.message)
            }
            val tried = ArrayList<String>()
            var lastReason: String? = null

            for (model in GeminiTextProtocol.tryOrder(names, workingModel).take(MAX_MODEL_ATTEMPTS)) {
                val request = Request.Builder()
                    .url("${GeminiTextProtocol.BASE_URL}/${GeminiTextProtocol.modelPath(model)}:generateContent")
                    .header(KEY_HEADER, key)
                    .post(requestBody.toRequestBody(JSON_TYPE))
                    .build()
                val result = withContext(Dispatchers.IO) { execute(request) }

                if (result.code in 200..299) {
                    workingModel = model // remember the one that works
                    return toReply(result.body)
                }
                val reason = GeminiTextProtocol.errorReason(result.body)
                logHttp(result.code, reason, key)
                if (result.code != 404) {
                    return ChatReplyResult.Error(GeminiTextProtocol.httpMessage(result.code, reason, key))
                }
                // 404: this listed model is retired or closed to this key. Try the next one.
                tried += GeminiTextProtocol.modelPath(model)
                lastReason = reason
                if (workingModel == model) workingModel = null
            }
            ChatReplyResult.Error(GeminiTextProtocol.noModelMessage(tried, lastReason, key))
        } catch (_: IOException) {
            ChatReplyResult.Error("Couldn't reach Gemini. Check your internet connection and try again.")
        }
    }

    private fun toReply(body: String): ChatReplyResult = when (val parsed = GeminiTextProtocol.parseReply(body)) {
        is ParsedReply.Text -> ChatReplyResult.Success(parsed.text)
        is ParsedReply.Blocked ->
            ChatReplyResult.Error("Gemini didn't answer that (${parsed.reason}). Try rephrasing your message.")
        ParsedReply.Empty -> ChatReplyResult.Error("Gemini sent back an empty reply. Please try again.")
    }

    private suspend fun resolve(apiKey: String): Resolution {
        candidates?.let { return Resolution.Models(it) }
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
            val names = GeminiTextProtocol.candidateModels(GeminiTextProtocol.parseModels(result.body))
            if (names.isEmpty()) return Resolution.Failure("Gemini has no chat model available for this key.")
            candidates = names
            Resolution.Models(names)
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
