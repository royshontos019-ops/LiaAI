package com.Lia.assistant.data

/**
 * Pure fallback rules (no Android classes) so they can be unit tested on the JVM.
 * "stored" = the DataStore value, "legacy" = the NovaPreferences value.
 */
object SettingsResolver {
    fun personality(stored: String?, legacy: String?): Personality =
        Personality.fromId(stored?.takeIf { it.isNotBlank() } ?: legacy)

    fun language(stored: String?, legacy: String?): LanguagePreference =
        LanguagePreference.fromId(stored?.takeIf { it.isNotBlank() } ?: legacy)

    fun text(stored: String?, legacy: String?, default: String): String =
        stored?.takeIf { it.isNotBlank() } ?: legacy?.takeIf { it.isNotBlank() } ?: default
}
