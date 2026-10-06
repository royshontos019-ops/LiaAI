package com.Lia.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceLatencyTrackerTest {
    private var now = 0L
    private val tracker = VoiceLatencyTracker(clock = { now })

    private fun at(t: Long, mark: LatencyMark): Boolean {
        now = t
        return tracker.mark(mark)
    }

    @Test fun inOrderMarksGiveTheRightDeltas() {
        assertTrue(at(100, LatencyMark.USER_SPEECH_START))
        assertTrue(at(600, LatencyMark.USER_SPEECH_END))
        assertTrue(at(900, LatencyMark.FIRST_AUDIO_RECEIVED))
        assertTrue(at(950, LatencyMark.FIRST_AUDIO_PLAYED))
        assertTrue(at(2_000, LatencyMark.TURN_COMPLETE))
        val l = tracker.current()
        assertEquals(500L, l.speechDurationMs)
        assertEquals(300L, l.responseLatencyMs)
        assertEquals(50L, l.playoutLatencyMs)
        assertEquals(350L, l.endToEndLatencyMs)
        assertEquals(1_100L, l.turnDurationMs)
    }

    @Test fun outOfOrderMarkIsIgnored() {
        assertTrue(at(100, LatencyMark.FIRST_AUDIO_RECEIVED))
        assertFalse("END comes before RECEIVED in the order", at(200, LatencyMark.USER_SPEECH_END))
        val l = tracker.current()
        assertNull(l.responseLatencyMs)
        assertNull(l.speechDurationMs)
    }

    @Test fun duplicateMarkKeepsTheFirstTimestamp() {
        assertTrue(at(10, LatencyMark.USER_SPEECH_START))
        assertFalse(at(50, LatencyMark.USER_SPEECH_START))
        assertTrue(at(110, LatencyMark.USER_SPEECH_END))
        assertEquals(100L, tracker.current().speechDurationMs)
    }

    @Test fun skippedMarksLeaveOnlyTheirDeltasEmpty() {
        at(0, LatencyMark.USER_SPEECH_END)
        at(400, LatencyMark.FIRST_AUDIO_RECEIVED)
        val l = tracker.current()
        assertEquals(400L, l.responseLatencyMs)
        assertNull(l.speechDurationMs)
        assertNull(l.playoutLatencyMs)
        assertNull(l.turnDurationMs)
    }

    @Test fun beginTurnClearsTheCurrentTurnButKeepsTheLastCompleted() {
        at(0, LatencyMark.USER_SPEECH_END)
        at(300, LatencyMark.FIRST_AUDIO_RECEIVED)
        at(900, LatencyMark.TURN_COMPLETE)
        assertEquals(600L, tracker.lastCompletedTurn()?.turnDurationMs)
        tracker.beginTurn()
        assertNull(tracker.current().turnDurationMs)
        assertEquals(600L, tracker.lastCompletedTurn()?.turnDurationMs)
        assertTrue("marks are accepted again after beginTurn", at(1_000, LatencyMark.USER_SPEECH_START))
    }

    @Test fun incompleteTurnIsNotStoredAsCompleted() {
        at(0, LatencyMark.USER_SPEECH_END)
        at(300, LatencyMark.FIRST_AUDIO_RECEIVED)
        assertNull(tracker.lastCompletedTurn())
    }

    @Test fun reconnectCounter() {
        assertEquals(0, tracker.reconnectCount.value)
        assertEquals(1, tracker.registerReconnectAttempt())
        assertEquals(2, tracker.registerReconnectAttempt())
        assertEquals(2, tracker.reconnectCount.value)
        tracker.resetReconnects()
        assertEquals(0, tracker.reconnectCount.value)
    }

    @Test fun resetClearsEverything() {
        at(0, LatencyMark.FIRST_AUDIO_RECEIVED)
        at(10, LatencyMark.TURN_COMPLETE)
        tracker.registerReconnectAttempt()
        tracker.reset()
        assertNull(tracker.lastCompletedTurn())
        assertEquals(0, tracker.reconnectCount.value)
        assertNotNull(tracker.current())
        assertNull(tracker.current().turnDurationMs)
    }
}
