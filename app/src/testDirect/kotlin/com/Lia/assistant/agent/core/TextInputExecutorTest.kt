package com.Lia.assistant.agent.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextInputExecutorTest {

    private val captionField = TargetSpec("Caption field", texts = listOf("Write a caption"), viewIdParts = listOf("caption"))

    private fun world(password: Boolean = false): FakeScreenDriver {
        val compose = FakeScreen(
            name = "compose",
            packageName = "com.example.app",
            nodes = listOf(
                fakeNode(text = "New post"),
                fakeNode(text = "Write a caption", viewId = "x:id/caption", clickable = true, editable = true, password = password),
                fakeNode(text = "Share", clickable = true),
            ),
        )
        return FakeScreenDriver(mapOf("compose" to compose), start = "compose")
    }

    private fun inputs(driver: FakeScreenDriver): TextInputExecutor {
        val timings = ActionTimings(resolveAttempts = 2, verifyAttempts = 2)
        val executor = UiActionExecutor(driver = driver, timings = timings, pause = {})
        return TextInputExecutor(driver, executor, timings, pause = {})
    }

    @Test
    fun typesTheTextAndVerifiesItIsThere() = runBlocking {
        val driver = world()
        val result = inputs(driver).typeInto(captionField, "Hello from Lia")

        assertEquals(ActionMethod.SET_TEXT, (result as ActionResult.Success).method)
        assertTrue(driver.log.contains("setText:Hello from Lia"))
    }

    @Test
    fun neverTypesIntoAPasswordField() = runBlocking {
        val driver = world(password = true)
        val result = inputs(driver).typeInto(captionField, "secret")

        assertEquals(FailureReason.PASSWORD_FIELD, (result as ActionResult.Failed).reason)
        assertFalse(driver.log.any { it.startsWith("setText:") })
        assertTrue(driver.clickLog.isEmpty())
    }

    @Test
    fun reportsWhenTypingIsNotSupported() = runBlocking {
        val driver = world().apply { setTextResult = DriverResult.UNSUPPORTED }
        val result = inputs(driver).typeInto(captionField, "Hello")

        assertEquals(FailureReason.UNSUPPORTED, (result as ActionResult.Failed).reason)
    }

    @Test
    fun failsWhenTheTextNeverShowsUp() = runBlocking {
        val driver = world().apply { applyTextOnSet = false }
        val result = inputs(driver).typeInto(captionField, "Hello")

        assertEquals(FailureReason.TEXT_NOT_ENTERED, (result as ActionResult.Failed).reason)
        assertEquals(ExecPhase.VERIFY, result.phase)
    }

    @Test
    fun aMissingFieldFailsBeforeTyping() = runBlocking {
        val driver = world()
        val result = inputs(driver).typeInto(TargetSpec("Nope", texts = listOf("No such field")), "Hello")

        assertEquals(FailureReason.TARGET_NOT_FOUND, (result as ActionResult.Failed).reason)
        assertFalse(driver.log.any { it.startsWith("setText:") })
    }

    @Test
    fun onlyEditableFieldsCountAsTargets() = runBlocking {
        val driver = world()
        // "Share" is clickable but not editable, so it can never be typed into.
        val result = inputs(driver).typeInto(TargetSpec("Share", texts = listOf("Share")), "Hello")

        assertTrue(result is ActionResult.Failed)
        assertFalse(driver.log.any { it.startsWith("setText:") })
    }

    @Test
    fun aBlockerStopsTyping() = runBlocking {
        val driver = world()
        val timings = ActionTimings(resolveAttempts = 2, verifyAttempts = 2)
        val executor = UiActionExecutor(driver = driver, blockerCheck = { Blocker.CAPTCHA }, timings = timings, pause = {})
        val result = TextInputExecutor(driver, executor, timings, pause = {}).typeInto(captionField, "Hello")

        assertEquals(Blocker.CAPTCHA, (result as ActionResult.Blocked).blocker)
        assertFalse(driver.log.any { it.startsWith("setText:") })
    }
}
