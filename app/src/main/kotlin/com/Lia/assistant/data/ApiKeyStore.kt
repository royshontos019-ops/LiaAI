package com.Lia.assistant.data

import android.content.Context

/**
 * Holds the Gemini API key the user types into Settings.
 * Never hardcode a key here and never log it (no Log calls, no toString of the key).
 */
object ApiKeyStore {
    private const val KEY = "gemini_api_key"

    internal fun normalize(raw: String): String = raw.trim()

    fun getKey(ctx: Context): String = NovaPreferences.getString(ctx, KEY, "")

    fun saveKey(ctx: Context, key: String) {
        val clean = normalize(key)
        if (clean.isEmpty()) NovaPreferences.remove(ctx, KEY)
        else NovaPreferences.putString(ctx, KEY, clean)
    }

    fun hasKey(ctx: Context): Boolean = getKey(ctx).isNotEmpty()
}
