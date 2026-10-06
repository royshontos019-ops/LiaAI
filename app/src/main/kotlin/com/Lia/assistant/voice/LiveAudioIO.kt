package com.Lia.assistant.voice

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * Microphone capture (16 kHz PCM16 mono) and speaker playback (24 kHz PCM16 mono) for the
 * Gemini Live session.
 *
 * Threading:
 *  - [onMicChunk] / [onMicAmplitude] are called on the dedicated "Lia-MicReader" thread, once
 *    per 2048-byte chunk. They are always called, even while [EchoGuard] says the mic is muted:
 *    the consumer should check `EchoGuard.isMicMuted()` before sending the audio to the server.
 *  - [playChunk] performs BLOCKING writes (back-pressure from the 4x buffer). Call it from a
 *    playback thread or an IO coroutine, never from the OkHttp listener thread.
 *  - [flushPlayback] may be called from any thread, also while [playChunk] is blocked.
 *
 * Latency choices: USAGE_ASSISTANT playback (not USAGE_VOICE_COMMUNICATION), a buffer of 4x
 * the minimum size, and no logging anywhere in the audio path.
 */
class LiveAudioIO(
    private val onMicChunk: (ByteArray) -> Unit,
    private val onMicAmplitude: (Float) -> Unit,
    private val onError: (String) -> Unit = {},
) {
    // ---------------- microphone ----------------
    private val micLock = Any()
    private var recorder: AudioRecord? = null
    private var micThread: Thread? = null
    @Volatile private var micRunning = false
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var gainControl: AutomaticGainControl? = null

    /** Starts capturing. Returns false (and calls onError) if the microphone cannot be opened. */
    @SuppressLint("MissingPermission")
    fun startMic(): Boolean {
        synchronized(micLock) {
            if (released) return false
            if (micThread != null) return true

            val minBuf = AudioRecord.getMinBufferSize(
                MIC_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            )
            val rec = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    MIC_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    max(minBuf, MIN_MIC_BUFFER_BYTES),
                )
            } catch (_: Exception) {
                onError("The microphone could not be opened. Check the microphone permission.")
                return false
            }
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                onError("The microphone could not be opened. Check the microphone permission.")
                return false
            }

            attachEffects(rec.audioSessionId)
            val started = try {
                rec.startRecording()
                rec.recordingState == AudioRecord.RECORDSTATE_RECORDING
            } catch (_: IllegalStateException) {
                false
            }
            if (!started) {
                rec.release()
                releaseEffects()
                onError("The microphone is busy or unavailable.")
                return false
            }

            recorder = rec
            micRunning = true
            micThread = Thread({ readLoop(rec) }, "Lia-MicReader").also { it.start() }
            return true
        }
    }

    /** Stops capturing and waits briefly for the reader thread to finish. */
    fun stopMic() {
        val rec: AudioRecord?
        val thread: Thread?
        synchronized(micLock) {
            micRunning = false
            rec = recorder
            thread = micThread
        }
        try { rec?.stop() } catch (_: Exception) {} // unblocks a pending read()
        if (thread != null && thread !== Thread.currentThread()) {
            try { thread.join(400) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
    }

    private fun readLoop(rec: AudioRecord) {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        } catch (_: Exception) {}
        val buf = ByteArray(MIC_CHUNK_BYTES)
        try {
            while (micRunning) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) {
                    val chunk = buf.copyOf(n)
                    onMicChunk(chunk)
                    onMicAmplitude(AudioMath.rms(chunk, n))
                } else if (n < 0) {
                    if (micRunning) onError("The microphone stopped delivering audio.")
                    break
                }
            }
        } catch (_: Throwable) {
            if (micRunning) onError("The microphone stopped unexpectedly.")
        } finally {
            cleanupMic(rec)
        }
    }

    private fun cleanupMic(rec: AudioRecord) {
        synchronized(micLock) {
            micRunning = false
            try { rec.stop() } catch (_: Exception) {}
            try { rec.release() } catch (_: Exception) {}
            releaseEffects()
            if (recorder === rec) {
                recorder = null
                micThread = null
            }
        }
    }

    private fun attachEffects(sessionId: Int) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { setEnabled(true) }
            }
        } catch (_: Throwable) {}
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { setEnabled(true) }
            }
        } catch (_: Throwable) {}
        try {
            if (AutomaticGainControl.isAvailable()) {
                gainControl = AutomaticGainControl.create(sessionId)?.apply { setEnabled(true) }
            }
        } catch (_: Throwable) {}
    }

    private fun releaseEffects() {
        try { echoCanceler?.release() } catch (_: Throwable) {}
        try { noiseSuppressor?.release() } catch (_: Throwable) {}
        try { gainControl?.release() } catch (_: Throwable) {}
        echoCanceler = null
        noiseSuppressor = null
        gainControl = null
    }

    // ---------------- speaker ----------------
    private val trackLock = Any()
    @Volatile private var track: AudioTrack? = null
    @Volatile private var volume = 1f
    @Volatile private var released = false
    private val flushGeneration = AtomicInteger(0)

    /**
     * Plays PCM16 24 kHz mono audio and returns this chunk's RMS level (0..1).
     * Blocks while the speaker buffer is full. Returns 0 if nothing could be played.
     */
    fun playChunk(pcm: ByteArray): Float {
        val len = pcm.size and 1.inv() // whole samples only
        if (len == 0 || released) return 0f
        val level = AudioMath.rms(pcm, len)
        val t = ensureTrack() ?: return 0f
        val generation = flushGeneration.get()
        var offset = 0
        while (offset < len) {
            // A flush (user interrupted the assistant) drops the rest of this chunk.
            if (generation != flushGeneration.get() || released) break
            val n = minOf(WRITE_SLICE_BYTES, len - offset)
            val written = try { t.write(pcm, offset, n) } catch (_: Exception) { -1 }
            if (written <= 0) break
            offset += written
        }
        return level
    }

    /** Drops everything queued for playback (pause + flush + play). Safe from any thread. */
    fun flushPlayback() {
        flushGeneration.incrementAndGet()
        val t = track ?: return
        try {
            t.pause()
            t.flush()
            t.play()
        } catch (_: IllegalStateException) {}
    }

    /** 0..1. Applies immediately and to a track created later. */
    fun setVolume(value: Float) {
        val v = value.coerceIn(0f, 1f)
        volume = v
        try { track?.setVolume(v) } catch (_: Exception) {}
    }

    private fun ensureTrack(): AudioTrack? {
        track?.let { return it }
        synchronized(trackLock) {
            track?.let { return it }
            if (released) return null
            val minBuf = AudioTrack.getMinBufferSize(
                SPEAKER_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
            )
            val bufferBytes = (if (minBuf > 0) minBuf else FALLBACK_MIN_SPEAKER_BUFFER_BYTES) * BUFFER_MULTIPLIER
            val t = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SPEAKER_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build(),
                    )
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (_: Exception) {
                onError("The speaker could not be opened.")
                return null
            }
            if (t.state != AudioTrack.STATE_INITIALIZED) {
                t.release()
                onError("The speaker could not be opened.")
                return null
            }
            try {
                t.setVolume(volume)
                t.play()
            } catch (_: Exception) {
                t.release()
                onError("The speaker could not be started.")
                return null
            }
            track = t
            return t
        }
    }

    /** Frees the microphone, the speaker and the audio effects. Safe to call more than once. */
    fun release() {
        released = true
        stopMic()
        flushGeneration.incrementAndGet()
        val t = synchronized(trackLock) { track.also { track = null } }
        if (t != null) {
            try { t.pause() } catch (_: Exception) {}
            try { t.flush() } catch (_: Exception) {}
            try { t.release() } catch (_: Exception) {}
        }
    }

    companion object {
        const val MIC_RATE = 16_000
        const val SPEAKER_RATE = 24_000
        const val MIC_CHUNK_BYTES = 2048
        private const val MIN_MIC_BUFFER_BYTES = 4096
        private const val BUFFER_MULTIPLIER = 4
        private const val FALLBACK_MIN_SPEAKER_BUFFER_BYTES = 4096
        private const val WRITE_SLICE_BYTES = 1920 // 40 ms at 24 kHz PCM16

        /** RMS of little-endian PCM16, normalised and clamped to 0..1. */
        fun rms(pcm: ByteArray, length: Int = pcm.size): Float = AudioMath.rms(pcm, length)
    }
}
