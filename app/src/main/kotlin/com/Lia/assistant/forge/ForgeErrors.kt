package com.Lia.assistant.forge

object ForgeErrors {
    const val STOPPED_EARLY = "Gemini stopped before finishing"
    const val NO_CONNECTION = "Couldn't reach Gemini. Check your internet connection and try again."
    const val NO_MODEL = "Gemini has no model available for this key. Check the API key in Settings."

    fun isOverloaded(code: Int, status: String?): Boolean =
        code == 503 || status.equals("UNAVAILABLE", ignoreCase = true)

    /** A sentence for the user. It never repeats Gemini's raw text, which could echo a key or a prompt. */
    fun friendly(code: Int, status: String?, message: String?): String = when {
        isOverloaded(code, status) -> "Gemini is busy right now. Please try again in a minute."
        code == 429 || status.equals("RESOURCE_EXHAUSTED", true) ->
            "Gemini's limit is used up for now. Wait a little, or check your quota."
        code == 400 && message?.contains("API key", ignoreCase = true) == true ->
            "Gemini does not accept this API key. Check it in Settings."
        code == 401 || code == 403 || status.equals("PERMISSION_DENIED", true) || status.equals("UNAUTHENTICATED", true) ->
            "Gemini refused this API key. Check it in Settings."
        code == 400 -> "Gemini could not process that request."
        code == 404 -> "That Gemini model is not available for your key."
        code >= 500 -> "Gemini had a problem on its side. Please try again."
        else -> "Gemini could not build the website (error $code)."
    }

    /** Text that is safe to log: the key removed, and cut short. */
    fun sanitize(text: String?, apiKey: String, max: Int = 200): String {
        var s = text.orEmpty()
        if (apiKey.isNotBlank()) s = s.replace(apiKey, "***")
        return s.take(max)
    }
}
