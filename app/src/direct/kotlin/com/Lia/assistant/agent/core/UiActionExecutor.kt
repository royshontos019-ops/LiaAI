package com.Lia.assistant.agent.core

import kotlinx.coroutines.delay

/**
 * Finds a target, taps it, and then CHECKS that what was expected really happened.
 *
 * - STALE or vanished targets: look again and retry (a few times).
 * - The accessibility click is tried first, then a gesture on the element's bounds.
 * - If a click was accepted but the expectation is not met, the action is NOT repeated by default,
 *   because tapping "Share" twice must never post twice. Pass [retryOnUnverified] only for taps that
 *   are safe to repeat (opening a tab, say).
 * - [blockerCheck] is asked about every screen; a blocker stops everything with [ActionResult.Blocked].
 */
class UiActionExecutor(
    private val driver: ScreenDriver,
    private val observer: ScreenObserver = ScreenObserver(),
    private val resolver: TargetResolver = TargetResolver(),
    private val blockerCheck: (ScreenObservation) -> Blocker? = { null },
    private val timings: ActionTimings = ActionTimings(),
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {

    suspend fun observe(): ScreenObservation? {
        val captured = driver.capture() ?: return null
        return observer.observe(captured)
    }

    /** Looks for [spec], waiting for a slow screen a few times before giving up. */
    suspend fun locate(spec: TargetSpec, expectedPackage: String? = null): Located {
        var last: ActionResult = ActionResult.Failed(
            FailureReason.TARGET_NOT_FOUND, "${spec.label} not found", ExecPhase.RESOLVE,
        )
        for (attempt in 1..timings.resolveAttempts) {
            val observation = observe()
            if (observation == null) {
                last = ActionResult.Failed(FailureReason.NO_SCREEN, "no readable screen", ExecPhase.OBSERVE)
            } else {
                val blocker = blockerCheck(observation)
                if (blocker != null) return Located.Stopped(ActionResult.Blocked(blocker, "on ${observation.packageName}"))

                if (expectedPackage != null && observation.packageName != expectedPackage) {
                    last = ActionResult.Failed(
                        FailureReason.WRONG_APP, "expected $expectedPackage but on ${observation.packageName}", ExecPhase.OBSERVE,
                    )
                } else {
                    when (val resolution = resolver.resolve(observation, spec)) {
                        is Resolution.Found -> return validate(observation, resolution.element, spec)
                        is Resolution.Ambiguous -> return Located.Stopped(
                            ActionResult.Failed(
                                FailureReason.TARGET_AMBIGUOUS,
                                "${resolution.candidates.size} matches for ${spec.label}",
                                ExecPhase.RESOLVE,
                            ),
                        )
                        Resolution.NotFound -> last = ActionResult.Failed(
                            FailureReason.TARGET_NOT_FOUND, "${spec.label} not found", ExecPhase.RESOLVE,
                        )
                    }
                }
            }
            if (attempt < timings.resolveAttempts) pause(timings.resolveIntervalMs)
        }
        return Located.Stopped(last)
    }

    private fun validate(observation: ScreenObservation, element: UiElement, spec: TargetSpec): Located {
        if (!element.enabled) {
            return Located.Stopped(
                ActionResult.Failed(FailureReason.TARGET_DISABLED, "${spec.label} is disabled", ExecPhase.VALIDATE),
            )
        }
        val x = element.bounds.centerX
        val y = element.bounds.centerY
        if (x < 0 || y < 0 || x > observation.screenWidth || y > observation.screenHeight) {
            return Located.Stopped(
                ActionResult.Failed(FailureReason.TARGET_OFFSCREEN, "${spec.label} is off screen", ExecPhase.VALIDATE),
            )
        }
        return Located.Found(observation, element)
    }

    suspend fun tap(
        spec: TargetSpec,
        expect: Expectation,
        expectedPackage: String? = null,
        retryOnUnverified: Boolean = false,
    ): ActionResult {
        var staleRetries = 0
        while (true) {
            val found = when (val located = locate(spec, expectedPackage)) {
                is Located.Found -> located
                is Located.Stopped -> return located.result
            }

            var sawFailure = false
            var sawStale = false
            var unverified = false

            for (method in CLICK_METHODS) {
                val driverResult = when (method) {
                    ActionMethod.ACCESSIBILITY_CLICK -> driver.clickElement(found.observation, found.element)
                    else -> driver.gestureTap(found.element.bounds.centerX, found.element.bounds.centerY)
                }
                when (driverResult) {
                    DriverResult.OK -> {
                        when (val verification = verify(expect, found.observation)) {
                            is Verification.Verified -> return ActionResult.Success(method, verification.observation)
                            is Verification.Stopped -> return verification.result
                            Verification.Unverified -> {
                                if (!retryOnUnverified) {
                                    return ActionResult.Failed(
                                        FailureReason.VERIFICATION_FAILED,
                                        "tapped ${spec.label} but the screen did not change as expected",
                                        ExecPhase.VERIFY,
                                    )
                                }
                                unverified = true
                            }
                        }
                    }
                    DriverResult.STALE, DriverResult.NOT_FOUND -> {
                        sawStale = true
                        break
                    }
                    DriverResult.FAILED -> sawFailure = true
                    DriverResult.UNSUPPORTED -> Unit
                }
            }

            if (sawStale) {
                staleRetries++
                if (staleRetries > timings.maxStaleRetries) {
                    return ActionResult.Failed(
                        FailureReason.STALE_OBSERVATION, "the screen kept changing under ${spec.label}", ExecPhase.EXECUTE,
                    )
                }
                continue
            }
            return when {
                unverified -> ActionResult.Failed(
                    FailureReason.VERIFICATION_FAILED,
                    "tapped ${spec.label} but the screen did not change as expected",
                    ExecPhase.VERIFY,
                )
                sawFailure -> ActionResult.Failed(FailureReason.ACTION_FAILED, "could not tap ${spec.label}", ExecPhase.EXECUTE)
                else -> ActionResult.Failed(FailureReason.UNSUPPORTED, "no way to tap ${spec.label}", ExecPhase.EXECUTE)
            }
        }
    }

    /** A tap at a point found by looking at the screen (for example by a vision model). */
    suspend fun tapAt(
        x: Int,
        y: Int,
        expect: Expectation,
        expectedPackage: String? = null,
    ): ActionResult {
        val before = observe()
            ?: return ActionResult.Failed(FailureReason.NO_SCREEN, "no readable screen", ExecPhase.OBSERVE)
        val blocker = blockerCheck(before)
        if (blocker != null) return ActionResult.Blocked(blocker, "on ${before.packageName}")
        if (expectedPackage != null && before.packageName != expectedPackage) {
            return ActionResult.Failed(
                FailureReason.WRONG_APP, "expected $expectedPackage but on ${before.packageName}", ExecPhase.OBSERVE,
            )
        }
        if (x < 0 || y < 0 || x > before.screenWidth || y > before.screenHeight) {
            return ActionResult.Failed(FailureReason.TARGET_OFFSCREEN, "($x, $y) is off screen", ExecPhase.VALIDATE)
        }
        return when (driver.gestureTap(x, y)) {
            DriverResult.OK -> when (val verification = verify(expect, before)) {
                is Verification.Verified -> ActionResult.Success(ActionMethod.VISION_TAP, verification.observation)
                is Verification.Stopped -> verification.result
                Verification.Unverified -> ActionResult.Failed(
                    FailureReason.VERIFICATION_FAILED, "tapped ($x, $y) but nothing changed as expected", ExecPhase.VERIFY,
                )
            }
            DriverResult.UNSUPPORTED -> ActionResult.Failed(FailureReason.UNSUPPORTED, "tapping by position is not possible")
            else -> ActionResult.Failed(FailureReason.ACTION_FAILED, "tap at ($x, $y) failed")
        }
    }

    private sealed interface Verification {
        class Verified(val observation: ScreenObservation) : Verification
        class Stopped(val result: ActionResult) : Verification
        object Unverified : Verification
    }

    private suspend fun verify(expect: Expectation, before: ScreenObservation): Verification {
        pause(timings.settleMs)
        for (attempt in 1..timings.verifyAttempts) {
            val after = observe()
            if (after != null) {
                val blocker = blockerCheck(after)
                if (blocker != null) return Verification.Stopped(ActionResult.Blocked(blocker, "on ${after.packageName}"))
                if (matches(expect, before, after)) return Verification.Verified(after)
            }
            if (attempt < timings.verifyAttempts) pause(timings.verifyIntervalMs)
        }
        return Verification.Unverified
    }

    /** True when [after] shows what [expect] asked for. */
    fun matches(expect: Expectation, before: ScreenObservation, after: ScreenObservation): Boolean = when (expect) {
        is Expectation.TargetGone -> resolver.resolve(after, expect.spec) is Resolution.NotFound
        is Expectation.TargetPresent -> resolver.resolve(after, expect.spec) !is Resolution.NotFound
        is Expectation.TextPresent -> after.containsText(expect.text)
        is Expectation.PackageIs -> after.packageName == expect.packageName
        Expectation.ScreenChanged -> after.signature != before.signature
        is Expectation.AnyOf -> expect.options.any { matches(it, before, after) }
        is Expectation.AllOf -> expect.options.all { matches(it, before, after) }
    }

    private companion object {
        val CLICK_METHODS = listOf(ActionMethod.ACCESSIBILITY_CLICK, ActionMethod.GESTURE_ON_ELEMENT_BOUNDS)
    }
}
