package com.Lia.assistant.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Software echo guard: while the assistant is speaking the microphone is "muted" so its own
 * voice is not sent back to the model.
 *
 *  - notifyPlaybackStarted()  -> counter++ and mute immediately
 *  - notifyPlaybackStopped()  -> counter--; at 0 the mic unmutes after [ECHO_TAIL_MS]
 *  - watchdog: if no further notifyPlaybackStarted() arrives for [WATCHDOG_MS] while muted
 *    (a Stopped call got lost), the guard force-unmutes and zeroes the counter.
 *
 * The consumer decides what "muted" means, e.g. `if (!EchoGuard.isMicMuted()) client.sendAudio(..)`.
 * Thread-safe. No logging.
 */
object EchoGuard {
    const val ECHO_TAIL_MS = 450L
    const val WATCHDOG_MS = 60_000L

    private val lock = Any()
    private var scope: CoroutineScope = newScope()

    private var active = 0
    @Volatile private var muted = false
    private var tailJob: Job? = null
    private var watchdogJob: Job? = null
    private var tailToken = 0L      // lets a stale timer recognise it was superseded
    private var watchdogToken = 0L

    fun isMicMuted(): Boolean = muted

    fun notifyPlaybackStarted() {
        synchronized(lock) {
            active++
            muted = true
            cancelTail()
            armWatchdog()
        }
    }

    fun notifyPlaybackStopped() {
        synchronized(lock) {
            if (active == 0) return // stray call (e.g. after the watchdog fired): ignore
            active--
            if (active == 0) {
                cancelWatchdog() // nothing is playing, nothing to guard against
                armTail()
            }
        }
    }

    /** Back to the initial state: unmuted, counter 0, no timers. */
    fun reset() {
        synchronized(lock) {
            active = 0
            muted = false
            cancelTail()
            cancelWatchdog()
        }
    }

    // ---- timers (all called with [lock] held) ----

    private fun armTail() {
        val token = ++tailToken
        tailJob?.cancel()
        tailJob = scope.launch {
            delay(ECHO_TAIL_MS)
            synchronized(lock) {
                if (token == tailToken && active == 0) {
                    muted = false
                    tailJob = null
                }
            }
        }
    }

    private fun cancelTail() {
        tailToken++
        tailJob?.cancel()
        tailJob = null
    }

    private fun armWatchdog() {
        val token = ++watchdogToken
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(WATCHDOG_MS)
            synchronized(lock) {
                if (token == watchdogToken) {
                    active = 0
                    muted = false
                    watchdogJob = null
                    cancelTail()
                }
            }
        }
    }

    private fun cancelWatchdog() {
        watchdogToken++
        watchdogJob?.cancel()
        watchdogJob = null
    }

    // ---- test hooks ----

    internal fun activePlaybackCount(): Int = synchronized(lock) { active }

    /** Runs the timers on [testScope] (virtual time) and resets all state. */
    internal fun configureForTest(testScope: CoroutineScope) {
        synchronized(lock) {
            reset()
            scope = testScope
        }
    }

    internal fun restoreDefaultScope() {
        synchronized(lock) {
            reset()
            scope = newScope()
        }
    }

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
