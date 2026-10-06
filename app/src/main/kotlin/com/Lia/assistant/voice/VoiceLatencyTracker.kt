package com.Lia.assistant.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/** Moments of one conversational turn, in the order they can happen. */
enum class LatencyMark { USER_SPEECH_START, USER_SPEECH_END, FIRST_AUDIO_RECEIVED, FIRST_AUDIO_PLAYED, TURN_COMPLETE }

/** Derived timings of one turn in milliseconds; null when a needed mark is missing. */
data class TurnLatency(
    val speechDurationMs: Long?,    // USER_SPEECH_START -> USER_SPEECH_END
    val responseLatencyMs: Long?,   // USER_SPEECH_END -> FIRST_AUDIO_RECEIVED
    val playoutLatencyMs: Long?,    // FIRST_AUDIO_RECEIVED -> FIRST_AUDIO_PLAYED
    val endToEndLatencyMs: Long?,   // USER_SPEECH_END -> FIRST_AUDIO_PLAYED
    val turnDurationMs: Long?,      // FIRST_AUDIO_RECEIVED -> TURN_COMPLETE
)

/**
 * Per-turn timestamps. A mark is accepted only if it comes later in the order than every mark
 * already accepted this turn; duplicates and out-of-order marks are ignored (first one wins).
 * Also holds the count of consecutive reconnect attempts. No logging.
 */
class VoiceLatencyTracker(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val stamps = LongArray(LatencyMark.entries.size) { NONE }
    private var lastOrdinal = -1
    private var lastCompleted: TurnLatency? = null

    private val _reconnectCount = MutableStateFlow(0)
    val reconnectCount: StateFlow<Int> = _reconnectCount.asStateFlow()

    /** Starts a fresh turn. The last completed turn stays available. */
    @Synchronized
    fun beginTurn() {
        stamps.fill(NONE)
        lastOrdinal = -1
    }

    /** Returns true if the mark was accepted. */
    @Synchronized
    fun mark(mark: LatencyMark): Boolean {
        if (mark.ordinal <= lastOrdinal) return false
        stamps[mark.ordinal] = clock()
        lastOrdinal = mark.ordinal
        if (mark == LatencyMark.TURN_COMPLETE) lastCompleted = snapshot()
        return true
    }

    @Synchronized fun current(): TurnLatency = snapshot()

    @Synchronized fun lastCompletedTurn(): TurnLatency? = lastCompleted

    /** Clears turn data and the reconnect counter (new voice session). */
    @Synchronized
    fun reset() {
        beginTurn()
        lastCompleted = null
        _reconnectCount.value = 0
    }

    /** Counts one more reconnect attempt and returns the new count. */
    fun registerReconnectAttempt(): Int = _reconnectCount.updateAndGet { it + 1 }

    fun resetReconnects() {
        _reconnectCount.value = 0
    }

    private fun delta(from: LatencyMark, to: LatencyMark): Long? {
        val a = stamps[from.ordinal]
        val b = stamps[to.ordinal]
        return if (a == NONE || b == NONE) null else b - a
    }

    private fun snapshot() = TurnLatency(
        speechDurationMs = delta(LatencyMark.USER_SPEECH_START, LatencyMark.USER_SPEECH_END),
        responseLatencyMs = delta(LatencyMark.USER_SPEECH_END, LatencyMark.FIRST_AUDIO_RECEIVED),
        playoutLatencyMs = delta(LatencyMark.FIRST_AUDIO_RECEIVED, LatencyMark.FIRST_AUDIO_PLAYED),
        endToEndLatencyMs = delta(LatencyMark.USER_SPEECH_END, LatencyMark.FIRST_AUDIO_PLAYED),
        turnDurationMs = delta(LatencyMark.FIRST_AUDIO_RECEIVED, LatencyMark.TURN_COMPLETE),
    )

    private companion object {
        const val NONE = Long.MIN_VALUE
    }
}
