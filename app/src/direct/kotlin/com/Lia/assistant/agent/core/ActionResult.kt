package com.Lia.assistant.agent.core

/** What an action reports back. */
sealed interface ActionResult {
    data class Success(
        val method: ActionMethod,
        val observation: ScreenObservation? = null,
        val detail: String = "",
    ) : ActionResult

    data class Failed(
        val reason: FailureReason,
        val detail: String = "",
        val phase: ExecPhase = ExecPhase.EXECUTE,
    ) : ActionResult

    /** The agent met something it must not get past alone (a login, a captcha...). */
    data class Blocked(val blocker: Blocker, val detail: String = "") : ActionResult
}

/** What should be true on screen after an action. Every action checks one of these. */
sealed interface Expectation {
    data class TargetGone(val spec: TargetSpec) : Expectation
    data class TargetPresent(val spec: TargetSpec) : Expectation
    data class TextPresent(val text: String) : Expectation
    data class PackageIs(val packageName: String) : Expectation
    object ScreenChanged : Expectation
    data class AnyOf(val options: List<Expectation>) : Expectation
    data class AllOf(val options: List<Expectation>) : Expectation
}

/** The result of looking for a target. */
sealed interface Located {
    data class Found(val observation: ScreenObservation, val element: UiElement) : Located

    /** Not found, ambiguous, disabled, off screen, wrong app, or blocked. [result] says which. */
    data class Stopped(val result: ActionResult) : Located
}

data class ActionTimings(
    val resolveAttempts: Int = 6,
    val resolveIntervalMs: Long = 500,
    val settleMs: Long = 400,
    val verifyAttempts: Int = 5,
    val verifyIntervalMs: Long = 500,
    val maxStaleRetries: Int = 2,
)
