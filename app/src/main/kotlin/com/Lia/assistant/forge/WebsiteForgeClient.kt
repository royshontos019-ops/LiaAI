package com.Lia.assistant.forge

import android.util.Log
import com.Lia.assistant.voice.GeminiTextClient
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Streams a page from Gemini over server-sent events. The model comes from GeminiTextClient's
 * ListModels lookup, so no model name is written here. Nothing secret is logged: not the key, not
 * the prompt, not the page.
 */
class WebsiteForgeClient(
    private val client: OkHttpClient = GeminiTextClient.http.newBuilder()
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build(),
) : SiteStreamer {

    private companion object {
        const val TAG = "WebsiteForgeClient"
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    override suspend fun stream(apiKey: String, request: ForgeRequestText, onText: (String) -> Unit): StreamResult =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            val model = GeminiTextClient.resolveModel(key) ?: return@withContext StreamResult.NoModel
            val path = if (model.startsWith("models/")) model else "models/$model"

            val http = Request.Builder()
                .url("$BASE_URL/$path:streamGenerateContent?alt=sse")
                .header("x-goog-api-key", key)
                .post(body(request, model).toRequestBody(JSON_TYPE))
                .build()

            val call = client.newCall(http)
            // When the collector is cancelled, cancel the network call too, so the read ends at once.
            val job = coroutineContext[Job]
            val handle = job?.invokeOnCompletion { cause -> if (cause != null) call.cancel() }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val error = SseParser.extractError(response.body?.string().orEmpty())
                        Log.w(TAG, "HTTP ${response.code} ${ForgeErrors.sanitize(error?.status, key, 60)}")
                        return@use StreamResult.HttpError(response.code, error?.status, error?.message)
                    }
                    val source = response.body?.source() ?: return@use StreamResult.Network
                    var finish: String? = null
                    while (true) {
                        coroutineContext.ensureActive()
                        val line = source.readUtf8Line() ?: break
                        val payload = SseParser.dataPayload(line) ?: continue
                        SseParser.extractError(payload)?.let { return@use StreamResult.HttpError(it.code, it.status, it.message) }
                        val text = SseParser.extractText(payload)
                        if (text.isNotEmpty()) onText(text)
                        SseParser.extractFinishReason(payload)?.let { finish = it }
                    }
                    if (finish != null && finish != "STOP") Log.w(TAG, "finishReason=$finish")
                    StreamResult.Completed(finish)
                }
            } catch (e: IOException) {
                coroutineContext.ensureActive() // a cancelled call lands here: turn it back into a cancellation
                StreamResult.Network
            } finally {
                handle?.dispose()
            }
        }

    private fun body(request: ForgeRequestText, model: String): String {
        val generation = JSONObject().put("temperature", 0.9)
        // 2.5 models "think" first, which delays the first word by about a minute. Switch that off.
        if (model.contains("2.5")) generation.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
        return JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", request.system))))
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", request.user))),
                ),
            )
            .put("generationConfig", generation)
            .toString()
    }
}
