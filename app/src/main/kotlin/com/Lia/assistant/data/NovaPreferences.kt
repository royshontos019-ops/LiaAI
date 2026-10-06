package com.Lia.assistant.data

import android.content.Context

/** Defaults used when nothing has been saved yet. */
object NovaDefaults {
    const val ORB_STYLE = "classic"
    const val VOICE_NAME = "Aoede"
    const val SPEECH_SPEED = 1.0f
    const val VOICE_VOLUME = 1.0f
}

/**
 * Thin wrapper over the "nova_prefs" SharedPreferences file.
 * Writes use apply() (async disk write, never blocks the caller).
 * The first read of the file loads it from disk, so [warmUp] is called from a background
 * thread at app start (see LiaApp).
 */
object NovaPreferences {
    const val FILE = "nova_prefs"

    object Keys {
        const val THEME_MODE = "theme_mode"
        const val REDUCED_MOTION = "reduced_motion"
        const val ORB_STYLE = "orb_style"
        const val PERSONALITY = "personality"
        const val USER_NAME = "user_name"
        const val VOICE_INPUT_ENABLED = "voice_input_enabled"
        const val WAKE_WORD_ENABLED = "wake_word_enabled"
        const val LANGUAGE = "language"
        const val HAS_ONBOARDED = "has_onboarded"
        const val VOICE_NAME = "voice_name"
        const val RESPONSE_STYLE = "response_style"
        const val SPEECH_SPEED = "speech_speed"
        const val VOICE_VOLUME = "voice_volume"
        const val ASSISTANT_NAME = "assistant_name"
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Forces the file to load. Call off the main thread. */
    fun warmUp(ctx: Context) {
        prefs(ctx).contains("")
    }

    fun getString(ctx: Context, key: String, default: String): String =
        prefs(ctx).getString(key, default) ?: default

    fun getStringOrNull(ctx: Context, key: String): String? = prefs(ctx).getString(key, null)

    fun putString(ctx: Context, key: String, value: String) {
        prefs(ctx).edit().putString(key, value).apply()
    }

    fun getBoolean(ctx: Context, key: String, default: Boolean): Boolean =
        prefs(ctx).getBoolean(key, default)

    fun putBoolean(ctx: Context, key: String, value: Boolean) {
        prefs(ctx).edit().putBoolean(key, value).apply()
    }

    fun getFloat(ctx: Context, key: String, default: Float): Float =
        prefs(ctx).getFloat(key, default)

    fun putFloat(ctx: Context, key: String, value: Float) {
        prefs(ctx).edit().putFloat(key, value).apply()
    }

    fun remove(ctx: Context, key: String) {
        prefs(ctx).edit().remove(key).apply()
    }
}
