package com.Lia.assistant.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.Lia.assistant.data.NovaPreferences.Keys
import com.Lia.assistant.voice.VoiceSessionManager
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

// One DataStore instance per process: keep this delegate in exactly one file.
private val Context.assistantStore: DataStore<Preferences> by preferencesDataStore(name = "assistant_settings")

object PersonalityRepository {
    private val PERSONALITY = stringPreferencesKey("personality")
    private val LANGUAGE = stringPreferencesKey("language")
    private val ASSISTANT_NAME = stringPreferencesKey("assistant_name")
    private val VOICE_NAME = stringPreferencesKey("voice_name")

    private fun data(ctx: Context): Flow<Preferences> =
        ctx.applicationContext.assistantStore.data.catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }

    // ---- Flows (DataStore value, falling back to NovaPreferences) ----

    fun personality(ctx: Context): Flow<Personality> = data(ctx).map {
        SettingsResolver.personality(it[PERSONALITY], NovaPreferences.getStringOrNull(ctx, Keys.PERSONALITY))
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    fun language(ctx: Context): Flow<LanguagePreference> = data(ctx).map {
        SettingsResolver.language(it[LANGUAGE], NovaPreferences.getStringOrNull(ctx, Keys.LANGUAGE))
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    fun assistantName(ctx: Context): Flow<String> = data(ctx).map {
        SettingsResolver.text(it[ASSISTANT_NAME], NovaPreferences.getStringOrNull(ctx, Keys.ASSISTANT_NAME), AssistantBrand.NAME)
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    fun voiceName(ctx: Context): Flow<String> = data(ctx).map {
        SettingsResolver.text(it[VOICE_NAME], NovaPreferences.getStringOrNull(ctx, Keys.VOICE_NAME), NovaDefaults.VOICE_NAME)
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    // ---- Suspend getters ----

    suspend fun getPersonality(ctx: Context): Personality = personality(ctx).first()
    suspend fun getLanguage(ctx: Context): LanguagePreference = language(ctx).first()
    suspend fun getAssistantName(ctx: Context): String = assistantName(ctx).first()
    suspend fun getVoiceName(ctx: Context): String = voiceName(ctx).first()

    // ---- Setters: DataStore + NovaPreferences, then refresh any live voice session ----

    suspend fun setPersonality(ctx: Context, value: Personality) {
        ctx.applicationContext.assistantStore.edit { it[PERSONALITY] = value.name }
        NovaPreferences.putString(ctx, Keys.PERSONALITY, value.name)
        VoiceSessionManager.refreshInstructions(ctx)
    }

    suspend fun setLanguage(ctx: Context, value: LanguagePreference) {
        ctx.applicationContext.assistantStore.edit { it[LANGUAGE] = value.name }
        NovaPreferences.putString(ctx, Keys.LANGUAGE, value.name)
        VoiceSessionManager.refreshInstructions(ctx)
    }

    suspend fun setAssistantName(ctx: Context, value: String) {
        val clean = value.trim().ifEmpty { AssistantBrand.NAME }
        ctx.applicationContext.assistantStore.edit { it[ASSISTANT_NAME] = clean }
        NovaPreferences.putString(ctx, Keys.ASSISTANT_NAME, clean)
        VoiceSessionManager.refreshInstructions(ctx)
    }

    suspend fun setVoiceName(ctx: Context, value: String) {
        val clean = value.trim().ifEmpty { NovaDefaults.VOICE_NAME }
        ctx.applicationContext.assistantStore.edit { it[VOICE_NAME] = clean }
        NovaPreferences.putString(ctx, Keys.VOICE_NAME, clean)
        VoiceSessionManager.refreshInstructions(ctx)
    }
}
