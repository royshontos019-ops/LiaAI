package com.Lia.assistant.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import com.Lia.assistant.data.NovaPreferences.Keys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ThemeMode {
    LIGHT, DARK, SYSTEM;

    companion object {
        val DEFAULT = DARK
        fun fromId(id: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(id?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/** Minimal in-memory conversation entry. Persistence comes in a later step. */
data class Conversation(val id: String, val title: String, val updatedAtMillis: Long)

/**
 * Remember once at the nav root with [rememberNovaAppState].
 * Fields start at their defaults and are filled from disk on a background thread by [load];
 * the UI can wait for [isLoaded]. Every field has an applyX() that updates the UI state
 * immediately and saves it (SharedPreferences apply() is non-blocking).
 * Named applyX (not setX) so they never clash with the generated JVM setters.
 */
class NovaAppState(
    private val appContext: Context,
    private val scope: CoroutineScope,
) {
    var isLoaded by mutableStateOf(false); private set

    var themeMode by mutableStateOf(ThemeMode.DEFAULT); private set
    var reducedMotion by mutableStateOf(false); private set
    var orbStyle by mutableStateOf(NovaDefaults.ORB_STYLE); private set
    var personality by mutableStateOf(Personality.DEFAULT); private set
    var assistantName by mutableStateOf(AssistantBrand.NAME); private set
    var userName by mutableStateOf(""); private set
    var language by mutableStateOf(LanguagePreference.DEFAULT); private set
    var voiceName by mutableStateOf(NovaDefaults.VOICE_NAME); private set
    var speechSpeed by mutableStateOf(NovaDefaults.SPEECH_SPEED); private set
    var voiceVolume by mutableStateOf(NovaDefaults.VOICE_VOLUME); private set

    val conversations: SnapshotStateList<Conversation> = mutableStateListOf()

    private class Snapshot(
        val themeMode: ThemeMode,
        val reducedMotion: Boolean,
        val orbStyle: String,
        val personality: Personality,
        val assistantName: String,
        val userName: String,
        val language: LanguagePreference,
        val voiceName: String,
        val speechSpeed: Float,
        val voiceVolume: Float,
    )

    private suspend fun readSnapshot(): Snapshot {
        val c = appContext
        return Snapshot(
            themeMode = ThemeMode.fromId(NovaPreferences.getStringOrNull(c, Keys.THEME_MODE)),
            reducedMotion = NovaPreferences.getBoolean(c, Keys.REDUCED_MOTION, false),
            orbStyle = NovaPreferences.getString(c, Keys.ORB_STYLE, NovaDefaults.ORB_STYLE),
            personality = PersonalityRepository.getPersonality(c),
            assistantName = PersonalityRepository.getAssistantName(c),
            userName = NovaPreferences.getString(c, Keys.USER_NAME, ""),
            language = PersonalityRepository.getLanguage(c),
            voiceName = PersonalityRepository.getVoiceName(c),
            speechSpeed = NovaPreferences.getFloat(c, Keys.SPEECH_SPEED, NovaDefaults.SPEECH_SPEED),
            voiceVolume = NovaPreferences.getFloat(c, Keys.VOICE_VOLUME, NovaDefaults.VOICE_VOLUME),
        )
    }

    /** Reads everything on Dispatchers.IO, then publishes it to the UI state. */
    suspend fun load() {
        val s = withContext(Dispatchers.IO) { readSnapshot() }
        themeMode = s.themeMode
        reducedMotion = s.reducedMotion
        orbStyle = s.orbStyle
        personality = s.personality
        assistantName = s.assistantName
        userName = s.userName
        language = s.language
        voiceName = s.voiceName
        speechSpeed = s.speechSpeed
        voiceVolume = s.voiceVolume
        isLoaded = true
    }

    fun applyThemeMode(value: ThemeMode) {
        themeMode = value
        NovaPreferences.putString(appContext, Keys.THEME_MODE, value.name)
    }

    fun applyReducedMotion(value: Boolean) {
        reducedMotion = value
        NovaPreferences.putBoolean(appContext, Keys.REDUCED_MOTION, value)
    }

    fun applyOrbStyle(value: String) {
        orbStyle = value
        NovaPreferences.putString(appContext, Keys.ORB_STYLE, value)
    }

    fun applyPersonality(value: Personality) {
        personality = value
        NovaPreferences.putString(appContext, Keys.PERSONALITY, value.name)
        scope.launch(Dispatchers.IO) { PersonalityRepository.setPersonality(appContext, value) }
    }

    fun applyAssistantName(value: String) {
        val clean = value.trim().ifEmpty { AssistantBrand.NAME }
        assistantName = clean
        NovaPreferences.putString(appContext, Keys.ASSISTANT_NAME, clean)
        scope.launch(Dispatchers.IO) { PersonalityRepository.setAssistantName(appContext, clean) }
    }

    fun applyUserName(value: String) {
        val clean = value.trim()
        userName = clean
        NovaPreferences.putString(appContext, Keys.USER_NAME, clean)
    }

    fun applyLanguage(value: LanguagePreference) {
        language = value
        NovaPreferences.putString(appContext, Keys.LANGUAGE, value.name)
        scope.launch(Dispatchers.IO) { PersonalityRepository.setLanguage(appContext, value) }
    }

    fun applyVoiceName(value: String) {
        val clean = value.trim().ifEmpty { NovaDefaults.VOICE_NAME }
        voiceName = clean
        NovaPreferences.putString(appContext, Keys.VOICE_NAME, clean)
        scope.launch(Dispatchers.IO) { PersonalityRepository.setVoiceName(appContext, clean) }
    }

    fun applySpeechSpeed(value: Float) {
        speechSpeed = value
        NovaPreferences.putFloat(appContext, Keys.SPEECH_SPEED, value)
    }

    fun applyVoiceVolume(value: Float) {
        voiceVolume = value
        NovaPreferences.putFloat(appContext, Keys.VOICE_VOLUME, value)
    }
}

@Composable
fun rememberNovaAppState(): NovaAppState {
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val state = remember { NovaAppState(appContext, scope) }
    LaunchedEffect(state) { state.load() }
    return state
}
