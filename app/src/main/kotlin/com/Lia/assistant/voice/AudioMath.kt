package com.Lia.assistant.voice

import kotlin.math.sqrt

/** Pure audio helpers (no Android classes) so they can be unit tested on the JVM. */
object AudioMath {
    /**
     * RMS level of little-endian PCM16 audio, normalised to 0..1.
     * Only the first [length] bytes are used; a trailing odd byte is ignored.
     */
    fun rms(pcm: ByteArray, length: Int = pcm.size): Float {
        val n = minOf(length, pcm.size) / 2
        if (n <= 0) return 0f
        var sum = 0.0
        for (i in 0 until n) {
            val lo = pcm[2 * i].toInt() and 0xFF
            val hi = pcm[2 * i + 1].toInt() // signed: carries the sign of the sample
            val sample = (hi shl 8) or lo
            sum += sample.toDouble() * sample
        }
        return (sqrt(sum / n) / 32768.0).toFloat().coerceIn(0f, 1f)
    }
}
