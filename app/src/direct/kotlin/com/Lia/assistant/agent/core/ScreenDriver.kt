package com.Lia.assistant.agent.core

/**
 * The ONLY boundary between the agent and the phone. Everything above this interface is plain
 * Kotlin that can be tested with a scripted fake; everything below it is Android.
 *
 * After any action that may change the screen, the driver treats earlier observations as stale:
 * acting on one returns [DriverResult.STALE] until the next [capture].
 */
interface ScreenDriver {
    /** Reads the screen that is showing now, or null when there is no readable window. */
    suspend fun capture(): CapturedScreen?

    suspend fun clickElement(observation: ScreenObservation, element: UiElement): DriverResult

    suspend fun gestureTap(x: Int, y: Int): DriverResult

    /** Replaces the text of the input field that has focus. */
    suspend fun setText(text: String): DriverResult

    suspend fun scroll(direction: ScrollDirection): DriverResult

    suspend fun back(): DriverResult

    suspend fun home(): DriverResult
}
