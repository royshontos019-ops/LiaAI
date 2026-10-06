package com.Lia.assistant.voice

import android.content.Context
import com.Lia.assistant.ActionExecutor
import com.Lia.assistant.Forge
import com.Lia.assistant.InstalledAppLabelCache
import com.Lia.assistant.ToolResult
import com.Lia.assistant.data.ApiKeyStore
import com.Lia.assistant.data.LanguagePreference
import com.Lia.assistant.data.NovaDefaults
import com.Lia.assistant.data.NovaPreferences
import com.Lia.assistant.data.Personality
import com.Lia.assistant.data.PersonalityRepository
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * The live voice session, independent of any screen. Screens only observe the StateFlows.
 *
 * Every client callback carries the session epoch it was created for; a callback whose epoch is
 * no longer current (old connection, stopped session) is ignored. No logging, no raw messages.
 */
object VoiceSessionManager {
    const val MAX_RECONNECTS = 5
    const val RECONNECT_DELAY_MS = 1_200L
    const val PLAYBACK_SILENCE_MS = 300L
    const val ERROR_CONNECTION_LOST = "Connection lost - tap Stop and reopen Voice Mode"
    const val ERROR_NO_KEY = "No Gemini API key - add it in Settings, then reopen Voice Mode"

    private const val VAD_THRESHOLD = 0.02f
    private const val VAD_HOLD_MS = 600L
    private const val THINKING_TIMEOUT_MS = 10_000L
    private const val TOOL_BUILD_WEBSITE = "build_website"

    private class PlayItem(val epoch: Int, val pcm: ByteArray)

    // ---------------- observable state ----------------
    private val _state = MutableStateFlow(NovaOrbState.IDLE)
    val state: StateFlow<NovaOrbState> = _state.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _currentPersonality = MutableStateFlow(Personality.DEFAULT)
    val currentPersonality: StateFlow<Personality> = _currentPersonality.asStateFlow()

    private val _currentLanguage = MutableStateFlow(LanguagePreference.DEFAULT)
    val currentLanguage: StateFlow<LanguagePreference> = _currentLanguage.asStateFlow()

    private val _detectedLanguage = MutableStateFlow(DetectedLanguage.UNKNOWN)
    val detectedLanguage: StateFlow<DetectedLanguage> = _detectedLanguage.asStateFlow()

    private val _vadActive = MutableStateFlow(false)
    val vadActive: StateFlow<Boolean> = _vadActive.asStateFlow()

    /** In memory only; wiped when the session stops. */
    val memory = ConversationMemory()
    val latency = VoiceLatencyTracker()

    // ---------------- internals ----------------
    private val lifecycleLock = Any()
    private val reconnectLock = Any()
    private val turnLock = Any()
    private val transcriptLock = Any()
    private val connectMutex = Mutex()
    private val sessionEpoch = AtomicInteger(0)
    private val pending = AtomicInteger(0)   // audio chunks queued or being written
    private val micStarted = AtomicBoolean(false)

    @Volatile private var appContext: Context? = null
    @Volatile private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var client: GeminiLiveClient? = null
    @Volatile private var audio: LiveAudioIO? = null
    @Volatile private var playbackChannel = Channel<PlayItem>(Channel.UNLIMITED)
    @Volatile private var reconnectJob: Job? = null
    @Volatile private var silenceJob: Job? = null
    @Volatile private var failed = false

    private var turnPlaying = false            // guarded by turnLock
    private val inputBuf = StringBuilder()     // guarded by transcriptLock
    private val outputBuf = StringBuilder()

    @Volatile private var turnOpen = false
    @Volatile private var heardSpeech = false
    @Volatile private var lastVoiceAt = 0L
    @Volatile private var thinkingSince = 0L

    private fun nowMs() = System.nanoTime() / 1_000_000
    private fun current(epoch: Int) = _isActive.value && epoch == sessionEpoch.get()

    // ---------------- public API ----------------

    /** Starts the voice session (no-op if already active). Call from the foreground service. */
    fun start(context: Context) {
        val app = context.applicationContext
        synchronized(lifecycleLock) {
            if (_isActive.value) return
            appContext = app
            resetRuntimeState()
            InstalledAppLabelCache.prewarm(app)

            val io = LiveAudioIO(
                onMicChunk = { onMicChunk(it) },
                onMicAmplitude = { onMicAmplitude(it) },
                onError = { fail(it) },
            )
            audio = io
            val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope = s
            val channel = Channel<PlayItem>(Channel.UNLIMITED)
            playbackChannel = channel
            _isActive.value = true
            setState(NovaOrbState.CONNECTING, "Connecting…")
            startPlaybackPump(s, io, channel)
            s.launch {
                io.setVolume(NovaPreferences.getFloat(app, NovaPreferences.Keys.VOICE_VOLUME, NovaDefaults.VOICE_VOLUME))
                connectMutex.withLock { connectSession(isResuming = false) }
            }
        }
    }

    /** Fully stops the session and frees the microphone and speaker. Safe to call repeatedly. */
    fun stop() {
        synchronized(lifecycleLock) {
            sessionEpoch.incrementAndGet()
            reconnectJob?.cancel()
            silenceJob?.cancel()
            scope.cancel()
            playbackChannel.close()
            client?.disconnect()
            client = null
            audio?.release()
            audio = null
            synchronized(turnLock) { turnPlaying = false }
            EchoGuard.reset()
            clearTranscripts()
            memory.clear()
            micStarted.set(false)
            pending.set(0)
            failed = false
            turnOpen = false
            heardSpeech = false
            _isActive.value = false
            _muted.value = false
            _vadActive.value = false
            _amplitude.value = 0f
            _state.value = NovaOrbState.IDLE
            _statusText.value = ""
        }
    }

    fun toggleMute() {
        val nowMuted = !_muted.value
        _muted.value = nowMuted
        if (nowMuted) _vadActive.value = false
        if (_state.value == NovaOrbState.LISTENING) _statusText.value = if (nowMuted) "Muted" else "Listening"
    }

    fun setVolume(volume: Float) {
        audio?.setVolume(volume)
    }

    /**
     * Called by PersonalityRepository after a settings change. Live cannot change the system
     * instruction mid-session, so reconnect and resume with a recap.
     */
    fun refreshInstructions(context: Context) {
        if (!_isActive.value || failed) return
        appContext = context.applicationContext
        scope.launch { connectMutex.withLock { if (_isActive.value && !failed) connectSession(isResuming = true) } }
    }

    // ---------------- connecting ----------------

    private suspend fun connectSession(isResuming: Boolean) {
        val ctx = appContext ?: return
        val epoch = sessionEpoch.incrementAndGet()
        if (!_isActive.value) return

        client?.disconnect()
        client = null
        resetPlayback()
        micStarted.compareAndSet(true, true) // mic keeps running across reconnects
        setState(NovaOrbState.CONNECTING, if (isResuming) "Reconnecting…" else "Connecting…")

        val personality = PersonalityRepository.getPersonality(ctx)
        val language = PersonalityRepository.getLanguage(ctx)
        val assistantName = PersonalityRepository.getAssistantName(ctx)
        val voiceName = PersonalityRepository.getVoiceName(ctx)
        if (epoch != sessionEpoch.get() || !_isActive.value) return

        _currentPersonality.value = personality
        _currentLanguage.value = language

        val key = ApiKeyStore.getKey(ctx)
        if (key.isBlank()) {
            fail(ERROR_NO_KEY)
            return
        }
        val prompt = PersonalityPromptBuilder.build(
            personality = personality,
            language = language,
            detectedLanguage = _detectedLanguage.value,
            recap = if (isResuming) memory.recap() else null,
            assistantName = assistantName,
        )
        val c = GeminiLiveClient(key, newListener(epoch))
        client = c
        c.connect(prompt, VoiceMapper.toGemini(voiceName))
    }

    private fun newListener(epoch: Int) = object : GeminiLiveClient.Listener {
        override fun onSetupComplete() {
            if (!current(epoch)) return
            latency.resetReconnects()
            _lastError.value = null
            setState(NovaOrbState.LISTENING, if (_muted.value) "Muted" else "Listening")
            if (micStarted.compareAndSet(false, true)) {
                scope.launch {
                    if (audio?.startMic() != true) micStarted.set(false) // audio.onError already reported
                }
            }
        }

        override fun onAudioChunk(pcm: ByteArray) {
            if (!current(epoch)) return
            val first = synchronized(turnLock) {
                val f = !turnPlaying
                turnPlaying = true
                f
            }
            if (first) {
                EchoGuard.notifyPlaybackStarted()
                if (!turnOpen) {
                    latency.beginTurn()
                    turnOpen = true
                }
                latency.mark(LatencyMark.FIRST_AUDIO_RECEIVED)
                heardSpeech = false
                _vadActive.value = false
                setState(NovaOrbState.SPEAKING, "Speaking")
            }
            pending.incrementAndGet()
            if (!playbackChannel.trySend(PlayItem(epoch, pcm)).isSuccess) pending.decrementAndGet()
        }

        override fun onInputTranscript(text: String) {
            if (!current(epoch)) return
            val sofar = synchronized(transcriptLock) {
                inputBuf.append(text)
                inputBuf.toString()
            }
            val lang = LanguageDetector.detect(sofar)
            if (lang != DetectedLanguage.UNKNOWN) _detectedLanguage.value = lang
        }

        override fun onOutputTranscript(text: String) {
            if (!current(epoch)) return
            synchronized(transcriptLock) { outputBuf.append(text) }
        }

        override fun onToolCall(id: String, name: String, args: JSONObject) {
            if (!current(epoch)) return
            scope.launch {
                val response = if (name == TOOL_BUILD_WEBSITE) {
                    // Start the Forge (it works in the background) and answer right away.
                    val started = appContext?.let { Forge.start(it, args) } ?: false
                    JSONObject().put("result", if (started) "forge_started" else "forge_unavailable")
                } else {
                    val result = try {
                        ActionExecutor.execute(name, args)
                    } catch (_: Exception) {
                        ToolResult(false, "The action failed.")
                    }
                    JSONObject().put("ok", result.ok).put("message", result.message)
                }
                if (current(epoch)) client?.sendToolResponse(id, name, response.toString())
            }
        }

        override fun onTurnComplete() {
            if (!current(epoch)) return
            commitTranscripts()
            latency.mark(LatencyMark.TURN_COMPLETE)
            turnOpen = false
            heardSpeech = false
            // If audio is still queued, the silence watchdog ends the turn once it has played.
            if (pending.get() == 0) endPlaybackTurn()
            if (!isTurnPlaying()) toListening()
        }

        override fun onInterrupted() {
            if (!current(epoch)) return
            drainPlayback()
            audio?.flushPlayback()
            commitTranscripts()
            turnOpen = false
            heardSpeech = false
            endPlaybackTurn()
            toListening()
        }

        override fun onError(message: String) {
            if (!current(epoch)) return
            _lastError.value = message
            // A rejected key cannot be fixed by retrying.
            if (message.contains("API key", ignoreCase = true)) fail(message)
        }

        override fun onClosed() {
            if (current(epoch)) scheduleReconnect()
        }
    }

    // ---------------- reconnect ----------------

    private fun scheduleReconnect() {
        val s = scope
        synchronized(reconnectLock) {
            if (!_isActive.value || failed) return
            if (reconnectJob?.isActive == true) return // single-flight
            reconnectJob = s.launch {
                val attempt = latency.registerReconnectAttempt()
                if (attempt > MAX_RECONNECTS) {
                    fail(ERROR_CONNECTION_LOST)
                    return@launch
                }
                setState(NovaOrbState.CONNECTING, "Reconnecting ($attempt/$MAX_RECONNECTS)…")
                resetPlayback()
                delay(RECONNECT_DELAY_MS)
                if (!_isActive.value || failed) return@launch
                connectMutex.withLock { connectSession(isResuming = true) }
            }
        }
    }

    /** Unrecoverable problem: stop everything, show ERROR, wait for the user to tap Stop. */
    private fun fail(message: String) {
        if (!_isActive.value) return
        failed = true
        sessionEpoch.incrementAndGet() // silences every callback of the old connection
        reconnectJob?.cancel()
        silenceJob?.cancel()
        client?.disconnect()
        client = null
        audio?.stopMic()
        micStarted.set(false)
        resetPlayback()
        _lastError.value = message
        _vadActive.value = false
        _amplitude.value = 0f
        _state.value = NovaOrbState.ERROR
        _statusText.value = message
    }

    // ---------------- microphone ----------------

    private fun onMicChunk(bytes: ByteArray) {
        if (_muted.value || EchoGuard.isMicMuted()) return
        client?.sendAudio(bytes)
    }

    private fun onMicAmplitude(level: Float) {
        if (!_isActive.value || failed) return
        val now = nowMs()
        val effective = if (_muted.value || EchoGuard.isMicMuted()) 0f else level

        val s = _state.value
        if (s == NovaOrbState.LISTENING || s == NovaOrbState.THINKING) _amplitude.value = effective

        if (effective >= VAD_THRESHOLD) {
            lastVoiceAt = now
            if (!_vadActive.value) {
                _vadActive.value = true
                if (!heardSpeech) {
                    if (!turnOpen) {
                        latency.beginTurn()
                        turnOpen = true
                    }
                    latency.mark(LatencyMark.USER_SPEECH_START)
                    heardSpeech = true
                }
            }
        } else if (_vadActive.value && now - lastVoiceAt > VAD_HOLD_MS) {
            _vadActive.value = false
            latency.mark(LatencyMark.USER_SPEECH_END)
            if (_state.value == NovaOrbState.LISTENING && heardSpeech) {
                thinkingSince = now
                setState(NovaOrbState.THINKING, "Thinking…")
            }
        }

        // Noise that never got an answer must not leave the orb thinking forever.
        if (_state.value == NovaOrbState.THINKING && now - thinkingSince > THINKING_TIMEOUT_MS) {
            heardSpeech = false
            toListening()
        }
    }

    // ---------------- playback ----------------

    private fun startPlaybackPump(s: CoroutineScope, io: LiveAudioIO, channel: Channel<PlayItem>) {
        s.launch {
            for (item in channel) {
                try {
                    if (item.epoch == sessionEpoch.get() && _isActive.value) {
                        val level = io.playChunk(item.pcm) // blocks until it fits the speaker buffer
                        if (item.epoch == sessionEpoch.get() && isTurnPlaying()) {
                            latency.mark(LatencyMark.FIRST_AUDIO_PLAYED)
                            _amplitude.value = level
                            if (_state.value != NovaOrbState.SPEAKING && _state.value != NovaOrbState.ERROR) {
                                setState(NovaOrbState.SPEAKING, "Speaking")
                            }
                        }
                    }
                } finally {
                    pending.decrementAndGet()
                }
                armSilenceWatchdog(item.epoch)
            }
        }
    }

    /** Ends the turn 300 ms after the last chunk was written, once nothing is queued. */
    private fun armSilenceWatchdog(epoch: Int) {
        silenceJob?.cancel()
        silenceJob = scope.launch {
            delay(PLAYBACK_SILENCE_MS)
            if (epoch != sessionEpoch.get() || !_isActive.value) return@launch
            if (pending.get() > 0) {
                armSilenceWatchdog(epoch)
                return@launch
            }
            endPlaybackTurn()
            toListening()
        }
    }

    private fun isTurnPlaying(): Boolean = synchronized(turnLock) { turnPlaying }

    /** Balances EchoGuard exactly once per started playback turn. */
    private fun endPlaybackTurn() {
        val wasPlaying = synchronized(turnLock) {
            val w = turnPlaying
            turnPlaying = false
            w
        }
        if (wasPlaying) EchoGuard.notifyPlaybackStopped()
    }

    private fun drainPlayback() {
        val channel = playbackChannel
        while (true) {
            val r = channel.tryReceive()
            if (!r.isSuccess) break
            pending.decrementAndGet()
        }
    }

    /** Drops queued audio and any playing turn (used on reconnect and failure). */
    private fun resetPlayback() {
        silenceJob?.cancel()
        drainPlayback()
        audio?.flushPlayback()
        commitTranscripts()
        endPlaybackTurn()
    }

    private fun toListening() {
        if (!_isActive.value || failed) return
        val s = _state.value
        if (s == NovaOrbState.SPEAKING || s == NovaOrbState.THINKING) {
            _amplitude.value = 0f
            setState(NovaOrbState.LISTENING, if (_muted.value) "Muted" else "Listening")
        }
    }

    // ---------------- helpers ----------------

    private fun setState(s: NovaOrbState, text: String) {
        _state.value = s
        _statusText.value = text
    }

    /** Moves the finished user/assistant text of this turn into the rolling memory. */
    private fun commitTranscripts() {
        val (user, assistant) = synchronized(transcriptLock) {
            val u = inputBuf.toString().trim()
            val a = outputBuf.toString().trim()
            inputBuf.setLength(0)
            outputBuf.setLength(0)
            u to a
        }
        if (user.isNotEmpty()) memory.add(Role.USER, user)
        if (assistant.isNotEmpty()) memory.add(Role.ASSISTANT, assistant)
    }

    private fun clearTranscripts() {
        synchronized(transcriptLock) {
            inputBuf.setLength(0)
            outputBuf.setLength(0)
        }
    }

    private fun resetRuntimeState() {
        sessionEpoch.incrementAndGet()
        failed = false
        micStarted.set(false)
        pending.set(0)
        synchronized(turnLock) { turnPlaying = false }
        clearTranscripts()
        memory.clear()
        latency.reset()
        EchoGuard.reset()
        turnOpen = false
        heardSpeech = false
        _muted.value = false
        _amplitude.value = 0f
        _vadActive.value = false
        _lastError.value = null
        _detectedLanguage.value = DetectedLanguage.UNKNOWN
    }
}
