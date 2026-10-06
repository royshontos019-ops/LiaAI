package com.Lia.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioMathTest {
    /** Little-endian PCM16 bytes for the given samples. */
    private fun pcm(vararg samples: Int): ByteArray {
        val out = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, s ->
            out[2 * i] = (s and 0xFF).toByte()
            out[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    @Test fun silenceIsZero() = assertEquals(0f, AudioMath.rms(pcm(0, 0, 0, 0)), 1e-6f)

    @Test fun emptyIsZero() = assertEquals(0f, AudioMath.rms(ByteArray(0)), 1e-6f)

    @Test fun constantHalfScale() = assertEquals(0.5f, AudioMath.rms(pcm(16384, 16384)), 1e-6f)

    @Test fun littleEndianByteOrder() =
        // 0x00 0x40 is 0x4000 = 16384 little-endian (it would be 64 if read big-endian)
        assertEquals(0.5f, AudioMath.rms(byteArrayOf(0x00, 0x40)), 1e-6f)

    @Test fun negativeSamplesCountLikePositive() =
        assertEquals(0.5f, AudioMath.rms(pcm(-16384, -16384)), 1e-6f)

    @Test fun mixedSignSquareWave() =
        assertEquals(32767f / 32768f, AudioMath.rms(pcm(32767, -32767, 32767, -32767)), 1e-5f)

    @Test fun mostNegativeSampleClampsToOne() = assertEquals(1f, AudioMath.rms(pcm(-32768)), 1e-6f)

    @Test fun trailingOddByteIsIgnored() =
        assertEquals(0.5f, AudioMath.rms(pcm(16384) + byteArrayOf(0x7F)), 1e-6f)

    @Test fun lengthLimitsWhatIsRead() =
        assertEquals(0.5f, AudioMath.rms(pcm(16384, 0, 0, 0), length = 2), 1e-6f)

    @Test fun companionDelegates() =
        assertEquals(0.5f, LiveAudioIO.rms(pcm(16384)), 1e-6f)
}
