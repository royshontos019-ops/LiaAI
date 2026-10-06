package com.Lia.assistant.voice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EchoGuardTest {
    @After fun tearDown() = EchoGuard.restoreDefaultScope()

    @Test fun startsUnmuted() = runTest {
        EchoGuard.configureForTest(backgroundScope)
        assertFalse(EchoGuard.isMicMuted())
        assertEquals(0, EchoGuard.activePlaybackCount())
    }

    @Test fun startMutesImmediately() = runTest {
        EchoGuard.configureForTest(backgroundScope)
        EchoGuard.notifyPlaybackStarted()
        assertTrue(EchoGuard.isMicMuted())
        assertEquals(1, EchoGuard.activePlaybackCount())
    }

    @Test fun unmutesOnlyAfterTheEchoTail() = runTest {
        EchoGuard.configureForTest(backgroundScope)
        EchoGuard.notifyPlaybackStarted()
        EchoGuard.notifyPlaybackStopped()
        assertTrue("still muted right after stop", EchoGuard.isMicMuted())
        advanceTimeBy(EchoGuard.ECHO_TAIL_MS - 1); runCurrent()
        assertTrue("still muted 1 ms before the tail ends", EchoGuard.isMicMuted())
        advanceTimeBy(1); runCurrent()
        assertFalse("unmuted when the tail ends", EchoGuard.isMicMuted())
    }

    @Test fun counterNeedsEveryStartToBeStopped() = runTest {
        EchoGuard.configureForTest(backgroundScope)
        EchoGuard.notifyPlaybackStarted()
        EchoGuard.notifyPlaybackStarted()
        EchoGuard.notifyPlaybackStopped()
        assertEquals(1, EchoGuard.activePlaybackCount())
        advanceTimeBy(10_000); runCurrent()
        assertTrue("one playback still active", EchoGuard.isMicMuted())
        EchoGuard.notifyPlaybackStopped()
        assertEquals(0, EchoGuard.activePlaybackCount())
        advanceTimeBy(EchoGuard.ECHO_TAIL_MS - 1); runCurrent()
        assertTrue(EchoGuard.isMicMuted())
        advanceTimeBy(1); runCurrent()
        assertFalse(EchoGuard.isMicMuted())
    }

    @Test fun newPlaybackDuringTheTailCancelsTheUnmute() = runTest {
        EchoGuard.configureForTest(backgroundScope)
        EchoGuard.notifyPlaybackStarted()
        EchoGuard.notifyPlaybackStopped()
        advanceTimeBy(200); runCurrent()
        EchoGuard.notifyPlaybackStarted()
        advanceTimeBy(1_000); runCurrent()
        assertTrue("the old tail must not unmute a new playback", EchoGuard.isMicMuted())
        EchoGuard.notifyPlaybackStopped()
        advanceTimeBy(EchoGuard.ECHO_TAIL_MS - 1); runCurrent()
        assertTrue(EchoGuard.isMicMuted())
        advanceTimeBy(1); runCurrent()
        assertFalse(EchoGuard.isMicMuted())
    }

    @Test fun strayStopIsIgnored() = runTest {
        EchoGuard.configureForTest(backgroundScope)
        EchoGuard.notifyPlaybackStopped()
        assertEquals(0, EchoGuard.activePlaybackCount())
        assertFalse(EchoGuard.isMicMuted())
    }

    @Test fun watchdogForceUnmutesAfter60Seconds() = runTest {
        EchoGuard.configureForTest(backgroundScope)
        EchoGuard.notifyPlaybackStarted()
        advanceTimeBy(EchoGuard.WATCHDOG_MS - 1); runCurrent()
        assertTrue(EchoGuard.isMicMuted())
        advanceTimeBy(1); runCurrent()
        assertFalse(EchoGuard.isMicMuted())
        assertEquals(0, EchoGuard.activePlaybackCount())
        // a late Stop after the watchdog must not push the counter below zero or mute again
        EchoGuard.notifyPlaybackStopped()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(0, EchoGuard.activePlaybackCount())
        assertFalse(EchoGuard.isMicMuted())
    }

    @Test fun watchdogRestartsOnEachStart() = runTest {
        EchoGuard.configureForTest(backgroundScope)
        EchoGuard.notifyPlaybackStarted()
        advanceTimeBy(50_000); runCurrent()
        EchoGuard.notifyPlaybackStarted()
        advanceTimeBy(EchoGuard.WATCHDOG_MS - 1); runCurrent()
        assertTrue(EchoGuard.isMicMuted())
        advanceTimeBy(1); runCurrent()
        assertFalse(EchoGuard.isMicMuted())
        assertEquals(0, EchoGuard.activePlaybackCount())
    }

    @Test fun watchdogIsCancelledOnceEverythingStopped() = runTest {
        EchoGuard.configureForTest(backgroundScope)
        EchoGuard.notifyPlaybackStarted()
        EchoGuard.notifyPlaybackStopped()
        advanceTimeBy(EchoGuard.ECHO_TAIL_MS); runCurrent()
        assertFalse(EchoGuard.isMicMuted())
        EchoGuard.notifyPlaybackStarted() // fresh playback long after
        advanceTimeBy(EchoGuard.WATCHDOG_MS - 1); runCurrent()
        assertTrue("old watchdog must not fire early", EchoGuard.isMicMuted())
    }

    @Test fun resetClearsEverything() = runTest {
        EchoGuard.configureForTest(backgroundScope)
        EchoGuard.notifyPlaybackStarted()
        EchoGuard.notifyPlaybackStarted()
        EchoGuard.reset()
        assertFalse(EchoGuard.isMicMuted())
        assertEquals(0, EchoGuard.activePlaybackCount())
        advanceTimeBy(EchoGuard.WATCHDOG_MS + 1_000); runCurrent()
        assertFalse(EchoGuard.isMicMuted())
        EchoGuard.notifyPlaybackStarted()
        assertTrue(EchoGuard.isMicMuted())
    }
}
