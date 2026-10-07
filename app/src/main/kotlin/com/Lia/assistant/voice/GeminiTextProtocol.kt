package com.Lia.assistant.voice

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class ModelInfo(val name: String, val methods: List<String>)

internal sealed interface ParsedReply {
    data class Text(val text: String) : ParsedReply
    data class Blocked(val reason: String) : ParsedReply
    data object Empty : ParsedReply
}

/** Pure JSON/text rules for the REST chat client, so they can be unit tested on the JVM. */
internal object GeminiTextProtocol {
    const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    const val MAX_HISTORY_TURNS = 40

    // ---------------- model list ----------------

    fun parseModels(json: String): List<ModelInfo> {
        val root = try { JSONObject(json) } catch (_: JSONException) { return emptyList() }
        val arr = root.optJSONArray("models") ?: return emptyList()
        val out = ArrayList<ModelInfo>(arr.length())
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val name = m.optString("name")
            if (name.isEmpty()) continue
            val methods = m.optJSONArray("supportedGenerationMethods")
                ?.let { a -> (0 until a.length()).map { a.optString(it) } }
                .orEmpty()
            out += ModelInfo(name, methods)
        }
        return out
    }

    private fun isLiveImageOrTts(lowerName: String) =
        "live" in lowerName || "image" in lowerName || "tts" in lowerName

    /**
     * Models to try, best first: every generateContent model whose name has "flash" but not
     * "live", "image" or "tts" (in the order Gemini lists them), then the other generateContent
     * models. Never a hardcoded id. A listed model can still answer 404 (retired, or closed to
     * new keys), so the client walks this list instead of trusting only the first entry.
     */
    fun candidateModels(models: List<ModelInfo>): List<String> {
        val usable = models.filter { "generateContent" in it.methods }
        val flash = usable.filter {
            val n = it.name.lowercase()
            "flash" in n && !isLiveImageOrTts(n)
        }
        val rest = usable.filter { it !in flash && !isLiveImageOrTts(it.name.lowercase()) }
        val ordered = (flash + rest).map { it.name }
        return ordered.ifEmpty { usable.map { it.name } }
    }

    /** First candidate: the first suitable flash model, else the first generateContent model. */
    fun pickModel(models: List<ModelInfo>): String? = candidateModels(models).firstOrNull()

    /** The model that last worked goes first; the others keep their order. */
    fun tryOrder(names: List<String>, working: String?): List<String> =
        if (working != null && working in names) listOf(working) + names.filter { it != working } else names

    /** Shown when every model we tried answered 404. Contains model ids, never the key. */
    fun noModelMessage(tried: List<String>, reason: String?, apiKey: String = ""): String {
        val detail = reason?.let { LiveErrors.sanitize(it, apiKey, 160) }?.takeIf { it.isNotBlank() }
        return "None of the Gemini models I tried could answer (HTTP 404): ${tried.joinToString(", ")}." +
            (detail?.let { " Gemini said: $it" } ?: "") +
            " Check that your API key can use Gemini models."
    }

    fun modelPath(name: String): String = if (name.startsWith("models/")) name else "models/$name"

    // ---------------- request ----------------

    /** Drops blanks, keeps the most recent turns, and makes sure the first turn is the user's. */
    fun prepareHistory(history: List<ChatTurn>): List<ChatTurn> =
        history.filter { it.text.isNotBlank() }.takeLast(MAX_HISTORY_TURNS).dropWhile { !it.isUser }

    /** Consecutive turns by the same side are merged, because Gemini expects alternating roles. */
    fun buildRequest(systemInstruction: String, history: List<ChatTurn>): String {
        val merged = ArrayList<Pair<String, String>>()
        for (turn in history) {
            val text = turn.text.trim()
            if (text.isEmpty()) continue
            val role = if (turn.isUser) "user" else "model"
            val last = merged.lastOrNull()
            if (last != null && last.first == role) {
                merged[merged.size - 1] = role to (last.second + "\n" + text)
            } else {
                merged += role to text
            }
        }
        val contents = JSONArray()
        for ((role, text) in merged) {
            contents.put(
                JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", text))),
            )
        }
        val root = JSONObject().put("contents", contents)
        if (systemInstruction.isNotBlank()) {
            root.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))))
        }
        return root.toString()
    }

    // ---------------- response ----------------

    fun parseReply(json: String): ParsedReply {
        val root = try { JSONObject(json) } catch (_: JSONException) { return ParsedReply.Empty }
        val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
        val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
        val text = buildString {
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    if (part.optBoolean("thought")) continue // reasoning summaries are not the answer
                    append(part.optString("text"))
                }
            }
        }.trim()
        if (text.isNotEmpty()) return ParsedReply.Text(text)

        val blockReason = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
        if (blockReason.isNotEmpty()) return ParsedReply.Blocked(blockReason)
        val finish = candidate?.optString("finishReason").orEmpty()
        if (finish.isNotEmpty() && finish != "STOP") return ParsedReply.Blocked(finish)
        return ParsedReply.Empty
    }

    /** Gemini's own explanation from an error body, or null. */
    fun errorReason(body: String): String? {
        val root = try { JSONObject(body) } catch (_: JSONException) { return null }
        return root.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
    }

    /** Text shown in the chat for a failed request. Never contains the key. */
    fun httpMessage(code: Int, reason: String?, apiKey: String = ""): String {
        val detail = reason?.let { LiveErrors.sanitize(it, apiKey, 200) }?.takeIf { it.isNotBlank() }
        return when (code) {
            400, 401, 403 ->
                "Gemini rejected the request (HTTP $code)" + (detail?.let { ": $it" } ?: "") +
                    " Check your API key in Settings."
            404 -> "Gemini couldn't find that model (HTTP 404). Please try again."
            429 -> "Gemini is limiting requests right now (HTTP 429). Please try again in a minute."
            in 500..599 -> "Gemini had a problem on its side (HTTP $code). Please try again."
            else -> "Gemini returned an error (HTTP $code)" + (detail?.let { ": $it" } ?: ".")
        }
    }
}
