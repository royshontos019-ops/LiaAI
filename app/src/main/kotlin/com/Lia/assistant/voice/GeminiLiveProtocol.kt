package com.Lia.assistant.voice

import java.net.URLEncoder
import java.util.Base64
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Events decoded from one server message. */
internal sealed interface LiveEvent {
    data object SetupComplete : LiveEvent
    class Audio(val pcm: ByteArray) : LiveEvent
    data class InputTranscript(val text: String) : LiveEvent
    data class OutputTranscript(val text: String) : LiveEvent
    class ToolCall(val id: String, val name: String, val args: JSONObject) : LiveEvent
    data object Interrupted : LiveEvent
    data object TurnComplete : LiveEvent
}

/** Builds the JSON the client sends. Pure, so it can be unit tested on the JVM. */
internal object LiveMessages {
    fun setup(model: String, voiceName: String, systemInstruction: String, declarations: JSONArray): String {
        val setup = JSONObject()
            .put("model", model)
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put(
                        "speechConfig",
                        JSONObject().put(
                            "voiceConfig",
                            JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName)),
                        ),
                    ),
            )
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))))
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
        // Deliberately NO realtimeInputConfig / automaticActivityDetection: server defaults apply.
        if (declarations.length() > 0) {
            setup.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", declarations)))
        }
        return JSONObject().put("setup", setup).toString()
    }

    fun audio(pcm: ByteArray, length: Int = pcm.size): String {
        val bytes = if (length == pcm.size) pcm else pcm.copyOf(length)
        val data = Base64.getEncoder().encodeToString(bytes) // basic encoder = no line wraps
        return JSONObject()
            .put(
                "realtimeInput",
                JSONObject().put(
                    "audio",
                    JSONObject()
                        .put("mimeType", "audio/pcm;rate=${GeminiLiveClient.INPUT_SAMPLE_RATE}")
                        .put("data", data),
                ),
            )
            .toString()
    }

    fun toolResponse(id: String, name: String, response: JSONObject): String =
        JSONObject()
            .put(
                "toolResponse",
                JSONObject().put(
                    "functionResponses",
                    JSONArray().put(JSONObject().put("id", id).put("name", name).put("response", response)),
                ),
            )
            .toString()
}

/** Decodes server messages. Unknown messages (goAway, sessionResumptionUpdate, ...) yield nothing. */
internal object LiveMessageParser {
    fun parse(raw: String): List<LiveEvent> {
        val root = try {
            JSONObject(raw)
        } catch (_: JSONException) {
            return emptyList()
        }
        val out = ArrayList<LiveEvent>(4)

        if (root.has("setupComplete")) out += LiveEvent.SetupComplete

        root.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { calls ->
            for (i in 0 until calls.length()) {
                val call = calls.optJSONObject(i) ?: continue
                val name = call.optString("name")
                if (name.isBlank()) continue
                out += LiveEvent.ToolCall(call.optString("id"), name, call.optJSONObject("args") ?: JSONObject())
            }
        }

        root.optJSONObject("serverContent")?.let { content ->
            content.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                for (i in 0 until parts.length()) {
                    val inline = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
                    if (!inline.optString("mimeType").startsWith("audio/pcm")) continue
                    val pcm = decode(inline.optString("data")) ?: continue
                    if (pcm.isNotEmpty()) out += LiveEvent.Audio(pcm)
                }
            }
            content.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }
                ?.let { out += LiveEvent.InputTranscript(it) }
            content.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }
                ?.let { out += LiveEvent.OutputTranscript(it) }
            if (content.optBoolean("interrupted")) out += LiveEvent.Interrupted
            if (content.optBoolean("turnComplete")) out += LiveEvent.TurnComplete
        }
        return out
    }

    private fun decode(data: String): ByteArray? =
        try {
            Base64.getDecoder().decode(data)
        } catch (_: IllegalArgumentException) {
            null
        }
}

/** Error text for the listener. Never contains the API key or raw message bodies. */
internal object LiveErrors {
    private val KEY_PARAM = Regex("key=[^&\\s\"']+")

    fun sanitize(text: String?, apiKey: String, maxLen: Int = 200): String {
        var s = text.orEmpty()
        if (apiKey.isNotBlank()) {
            s = s.replace(apiKey, "***").replace(URLEncoder.encode(apiKey, "UTF-8"), "***")
        }
        s = KEY_PARAM.replace(s, "key=***")
        return if (s.length > maxLen) s.take(maxLen) + "…" else s
    }

    fun describeFailure(t: Throwable, httpCode: Int?, apiKey: String): String = when {
        httpCode == 400 || httpCode == 401 || httpCode == 403 ->
            "The server rejected the request (HTTP $httpCode). Check that the Gemini API key in Settings is correct."
        httpCode == 429 -> "Rate limit or quota reached (HTTP 429). Try again later."
        httpCode != null -> "Server error (HTTP $httpCode)."
        else -> {
            val detail = sanitize(t.message, apiKey, 160)
            "Connection problem (${t.javaClass.simpleName})" + if (detail.isNotBlank()) ": $detail" else ""
        }
    }

    fun describeClose(code: Int, reason: String, apiKey: String): String {
        val detail = sanitize(reason, apiKey)
        return "The server closed the connection (code $code)" + if (detail.isNotBlank()) ": $detail" else "."
    }
}
