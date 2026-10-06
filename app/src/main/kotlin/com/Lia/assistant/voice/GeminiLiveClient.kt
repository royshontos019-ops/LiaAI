package com.Lia.assistant.voice

import com.Lia.assistant.LiaToolCatalog
import com.Lia.assistant.data.NovaDefaults
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject

/**
 * Gemini Live API client over an OkHttp WebSocket.
 *
 * Listener callbacks run on OkHttp's reader thread: return quickly and hop to your own
 * coroutine/thread for real work. Audio in = 16 kHz PCM16 mono, audio out = 24 kHz PCM16 mono.
 *
 * The API key is never logged, never put in an error message, and no raw server message is logged.
 */
class GeminiLiveClient(
    apiKey: String,
    private val listener: Listener,
    private val http: OkHttpClient = defaultHttpClient(),
) {
    interface Listener {
        fun onSetupComplete() {}
        fun onAudioChunk(pcm: ByteArray) {}
        fun onInputTranscript(text: String) {}
        fun onOutputTranscript(text: String) {}
        fun onToolCall(id: String, name: String, args: JSONObject) {}
        fun onTurnComplete() {}
        fun onInterrupted() {}
        fun onError(message: String) {}
        fun onClosed() {}
    }

    private val apiKey = apiKey.trim()
    private val dedup = ToolCallDeduplicator()
    private val lock = Any()
    @Volatile private var session: Session? = null

    /** True once the server has acknowledged the setup; audio is only sent from then on. */
    val isReady: Boolean get() = session?.ready == true

    /** Opens a new session (any previous one is closed silently). Result arrives via the listener. */
    fun connect(systemInstruction: String, voiceName: String = NovaDefaults.VOICE_NAME) {
        if (apiKey.isEmpty()) {
            listener.onError("No Gemini API key set. Add it in Settings.")
            return
        }
        val voice = voiceName.trim().ifEmpty { NovaDefaults.VOICE_NAME }
        val setupJson = LiveMessages.setup(MODEL, voice, systemInstruction, LiaToolCatalog.declarations())
        val fresh = Session(setupJson)
        val previous = synchronized(lock) { session.also { session = fresh } }
        previous?.silentClose()
        dedup.clear()
        val request = Request.Builder().url(wsUrl(apiKey)).build()
        fresh.socket = http.newWebSocket(request, fresh)
    }

    /** Sends PCM16 16 kHz mono audio. Returns false (and drops it) if the session is not ready. */
    fun sendAudio(pcm: ByteArray, length: Int = pcm.size): Boolean {
        val s = session ?: return false
        if (!s.ready || length <= 0 || length > pcm.size) return false
        return s.socket?.send(LiveMessages.audio(pcm, length)) ?: false
    }

    /** Answers a tool call. [resultJson] should be a JSON object string; anything else is wrapped. */
    fun sendToolResponse(id: String, name: String, resultJson: String): Boolean {
        val s = session ?: return false
        if (!s.ready) return false
        val response = try {
            JSONObject(resultJson)
        } catch (_: Exception) {
            JSONObject().put("result", resultJson)
        }
        return s.socket?.send(LiveMessages.toolResponse(id, name, response)) ?: false
    }

    /** Closes with code 1000 and reports onClosed once. Safe to call repeatedly. */
    fun disconnect() {
        val s = synchronized(lock) { session.also { session = null } } ?: return
        dedup.clear()
        if (s.finished.compareAndSet(false, true)) {
            s.active = false
            s.ready = false
            s.socket?.close(NORMAL_CLOSE, "client disconnect")
            listener.onClosed()
        }
    }

    private fun dispatch(event: LiveEvent) {
        when (event) {
            LiveEvent.SetupComplete -> listener.onSetupComplete()
            is LiveEvent.Audio -> listener.onAudioChunk(event.pcm)
            is LiveEvent.InputTranscript -> listener.onInputTranscript(event.text)
            is LiveEvent.OutputTranscript -> listener.onOutputTranscript(event.text)
            is LiveEvent.ToolCall ->
                if (dedup.shouldHandle(event.id)) listener.onToolCall(event.id, event.name, event.args)
            LiveEvent.Interrupted -> listener.onInterrupted()
            LiveEvent.TurnComplete -> listener.onTurnComplete()
        }
    }

    /** One WebSocket connection. Callbacks from an old or finished session are ignored. */
    private inner class Session(private val setupJson: String) : WebSocketListener() {
        @Volatile var socket: WebSocket? = null
        @Volatile var ready = false
        @Volatile var active = true
        val finished = AtomicBoolean(false)

        fun silentClose() {
            active = false
            ready = false
            finished.set(true)
            socket?.close(NORMAL_CLOSE, "replaced")
        }

        private fun finish() {
            if (finished.compareAndSet(false, true)) {
                active = false
                ready = false
                synchronized(lock) { if (session === this) session = null }
                listener.onClosed()
            }
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!active) {
                webSocket.close(NORMAL_CLOSE, null)
                return
            }
            socket = webSocket
            if (!webSocket.send(setupJson)) {
                listener.onError("Could not send the session setup.")
                webSocket.cancel()
                finish()
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) = handle(text)

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.utf8())

        private fun handle(raw: String) {
            if (!active) return
            try {
                for (event in LiveMessageParser.parse(raw)) {
                    if (!active) return
                    if (event === LiveEvent.SetupComplete) ready = true
                    dispatch(event)
                }
            } catch (_: Exception) {
                listener.onError("Could not process a message from the server.")
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (!active) return
            if (code != NORMAL_CLOSE) listener.onError(LiveErrors.describeClose(code, reason, apiKey))
            webSocket.close(NORMAL_CLOSE, null)
            finish()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = finish()

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!active) return
            listener.onError(LiveErrors.describeFailure(t, response?.code, apiKey))
            finish()
        }
    }

    companion object {
        /**
         * The ONLY place the Live model name lives. Checked against the Gemini API docs
         * (deprecations page, Oct 2026): gemini-3.1-flash-live-preview is the current
         * audio-to-audio Live model; no shutdown date announced.
         */
        const val MODEL = "models/gemini-3.1-flash-live-preview"

        const val WS_URL_BASE =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

        const val INPUT_SAMPLE_RATE = 16_000
        const val OUTPUT_SAMPLE_RATE = 24_000
        private const val NORMAL_CLOSE = 1000

        /** WS_URL_BASE?key=<API_KEY> (key URL-encoded). */
        fun wsUrl(apiKey: String): String = WS_URL_BASE + "?key=" + URLEncoder.encode(apiKey.trim(), "UTF-8")

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // 0 = no read timeout (long-lived stream)
            .connectTimeout(20, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
}
