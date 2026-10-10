package com.Lia.assistant.forge

import org.json.JSONObject

data class ApiError(val code: Int, val status: String?, val message: String?)

/** Reads the lines of a Gemini `alt=sse` stream. Pure: no Android classes besides org.json. */
object SseParser {

    /** The JSON after "data:", or null for blank lines, comments and the [DONE] marker. */
    fun dataPayload(line: String): String? {
        if (!line.startsWith("data:")) return null
        val payload = line.substring(5).trim()
        return if (payload.isEmpty() || payload == "[DONE]") null else payload
    }

    /** All the text of the first candidate, parts joined. Parts marked as thoughts are skipped. */
    fun extractText(json: String): String {
        val root = parse(json) ?: return ""
        val parts = root.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        val out = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            if (part.optBoolean("thought", false)) continue
            out.append(part.optString("text", ""))
        }
        return out.toString()
    }

    fun extractError(json: String): ApiError? {
        val error = parse(json)?.optJSONObject("error") ?: return null
        return ApiError(
            code = error.optInt("code", 0),
            status = error.optString("status", "").ifEmpty { null },
            message = error.optString("message", "").ifEmpty { null },
        )
    }

    fun extractFinishReason(json: String): String? =
        parse(json)?.optJSONArray("candidates")?.optJSONObject(0)?.optString("finishReason", "")?.ifEmpty { null }

    private fun parse(json: String): JSONObject? = try {
        JSONObject(json)
    } catch (e: Exception) {
        null
    }
}
