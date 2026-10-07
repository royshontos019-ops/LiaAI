package com.Lia.assistant.ui.screens.chat

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.Lia.assistant.voice.DetectedLanguage
import com.Lia.assistant.voice.LanguageDetector
import java.util.Locale

/** Reads assistant replies aloud. Create once per screen and call [shutdown] when it leaves. */
class SpeechController(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false

    var speaking by mutableStateOf(false)
        private set

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            main.post { speaking = true }
        }

        override fun onDone(utteranceId: String?) {
            main.post { speaking = false }
        }

        @Deprecated("Deprecated in the framework")
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onError(utteranceId: String?) {
            main.post { speaking = false }
        }
    }

    init {
        tts = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) tts?.setOnUtteranceProgressListener(listener)
        }
    }

    /** Returns false if speech is not ready or there is nothing to say. */
    fun speak(text: String): Boolean {
        val engine = tts?.takeIf { ready } ?: return false
        val clean = SpeechText.clean(text)
        if (clean.isEmpty()) return false
        val locale = if (LanguageDetector.detect(clean) == DetectedLanguage.HINDI) {
            Locale.Builder().setLanguage("hi").setRegion("IN").build()
        } else {
            Locale.getDefault()
        }
        val set = engine.setLanguage(locale)
        if (set == TextToSpeech.LANG_MISSING_DATA || set == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
        }
        engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        speaking = true
        return true
    }

    fun stop() {
        try { tts?.stop() } catch (_: Exception) {}
        speaking = false
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        ready = false
        speaking = false
    }

    private companion object {
        const val UTTERANCE_ID = "lia-chat"
    }
}
