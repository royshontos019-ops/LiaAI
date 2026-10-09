package com.Lia.assistant.agent.core

import kotlinx.coroutines.delay

/**
 * Types text into a field and checks that the text is really there afterwards.
 * It refuses password fields: the agent never types or reads passwords.
 */
class TextInputExecutor(
    private val driver: ScreenDriver,
    private val executor: UiActionExecutor,
    private val timings: ActionTimings = ActionTimings(),
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {

    suspend fun typeInto(field: TargetSpec, text: String, expectedPackage: String? = null): ActionResult {
        val found = when (val located = executor.locate(field.copy(mustBeEditable = true), expectedPackage)) {
            is Located.Found -> located
            is Located.Stopped -> return located.result
        }
        val element = found.element
        if (element.password) {
            return ActionResult.Failed(
                FailureReason.PASSWORD_FIELD, "${field.label} is a password field", ExecPhase.VALIDATE,
            )
        }

        if (!element.focused) {
            val click = driver.clickElement(found.observation, element)
            if (click != DriverResult.OK) {
                val tap = driver.gestureTap(element.bounds.centerX, element.bounds.centerY)
                if (tap != DriverResult.OK) {
                    return ActionResult.Failed(
                        FailureReason.ACTION_FAILED, "could not focus ${field.label}", ExecPhase.EXECUTE,
                    )
                }
            }
        }

        when (driver.setText(text)) {
            DriverResult.OK -> Unit
            DriverResult.UNSUPPORTED -> return ActionResult.Failed(
                FailureReason.UNSUPPORTED, "typing into ${field.label} is not possible here", ExecPhase.EXECUTE,
            )
            DriverResult.NOT_FOUND -> return ActionResult.Failed(
                FailureReason.TARGET_NOT_FOUND, "no input field has focus", ExecPhase.EXECUTE,
            )
            else -> return ActionResult.Failed(
                FailureReason.ACTION_FAILED, "could not type into ${field.label}", ExecPhase.EXECUTE,
            )
        }

        pause(timings.settleMs)
        for (attempt in 1..timings.verifyAttempts) {
            val after = executor.observe()
            if (after != null && textIsThere(after, text)) {
                return ActionResult.Success(ActionMethod.SET_TEXT, after)
            }
            if (attempt < timings.verifyAttempts) pause(timings.verifyIntervalMs)
        }
        return ActionResult.Failed(
            FailureReason.TEXT_NOT_ENTERED, "the text did not appear in ${field.label}", ExecPhase.VERIFY,
        )
    }

    private fun textIsThere(screen: ScreenObservation, wanted: String): Boolean {
        val want = normalize(wanted)
        return screen.elements.any { element ->
            if (!element.editable || element.password) return@any false
            val have = normalize(element.text)
            have == want || (want.length > 200 && have.contains(want.take(200)))
        }
    }

    private fun normalize(value: String): String = value.trim().replace(Regex("\\s+"), " ")
}
