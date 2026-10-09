package com.Lia.assistant.agent.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiActionExecutorTest {

    private val create = TargetSpec("Create button", texts = listOf("Create"), exact = true)
    private val goesToPicker = Expectation.TextPresent("New post")

    private fun world(
        createEnabled: Boolean = true,
        packageName: String = "com.example.app",
        extraNodes: List<CapturedNode> = emptyList(),
    ): FakeScreenDriver {
        val feed = FakeScreen(
            name = "feed",
            packageName = packageName,
            nodes = listOf(
                fakeNode(text = "Home"),
                fakeNode(text = "Create", clickable = true, enabled = createEnabled),
                fakeNode(text = "Search", clickable = true),
            ) + extraNodes,
            transitions = mapOf("Create" to "picker"),
        )
        val picker = FakeScreen(
            name = "picker",
            packageName = packageName,
            nodes = listOf(fakeNode(text = "New post"), fakeNode(text = "Cancel", clickable = true)),
            backTo = "feed",
        )
        return FakeScreenDriver(mapOf("feed" to feed, "picker" to picker), start = "feed")
    }

    private fun executor(
        driver: FakeScreenDriver,
        blocker: Blocker? = null,
        timings: ActionTimings = ActionTimings(resolveAttempts = 3, verifyAttempts = 2, maxStaleRetries = 2),
    ) = UiActionExecutor(
        driver = driver,
        blockerCheck = { blocker },
        timings = timings,
        pause = {},
    )

    @Test
    fun tapsWithAnAccessibilityClickAndVerifiesTheResult() = runBlocking {
        val driver = world()
        val result = executor(driver).tap(create, goesToPicker)

        assertTrue(result is ActionResult.Success)
        assertEquals(ActionMethod.ACCESSIBILITY_CLICK, (result as ActionResult.Success).method)
        assertEquals("picker", driver.current)
        assertTrue(driver.gestureLog.isEmpty())
    }

    @Test
    fun verifiesThatTheTargetIsGone() = runBlocking {
        val result = executor(world()).tap(create, Expectation.TargetGone(create))
        assertTrue(result is ActionResult.Success)
    }

    @Test
    fun verifiesThatTheScreenChanged() = runBlocking {
        val result = executor(world()).tap(create, Expectation.ScreenChanged)
        assertTrue(result is ActionResult.Success)
    }

    @Test
    fun fallsBackToAGestureWhenTheAccessibilityClickFails() = runBlocking {
        val driver = world().apply { accessibilityClick = DriverResult.FAILED }
        val result = executor(driver).tap(create, goesToPicker)

        assertEquals(ActionMethod.GESTURE_ON_ELEMENT_BOUNDS, (result as ActionResult.Success).method)
        assertEquals(1, driver.gestureLog.size)
        assertEquals("picker", driver.current)
    }

    @Test
    fun anAcceptedClickWithNoEffectIsNotRepeatedByDefault() = runBlocking {
        val driver = world().apply { accessibilityClickHasEffect = false }
        val result = executor(driver).tap(create, goesToPicker)

        assertTrue(result is ActionResult.Failed)
        assertEquals(FailureReason.VERIFICATION_FAILED, (result as ActionResult.Failed).reason)
        assertEquals(ExecPhase.VERIFY, result.phase)
        assertEquals(1, driver.clickLog.size)
        assertTrue("no second attempt may happen", driver.gestureLog.isEmpty())
    }

    @Test
    fun anUnverifiedTapMayBeRepeatedOnlyWhenTheCallerSaysItIsSafe() = runBlocking {
        val driver = world().apply { accessibilityClickHasEffect = false }
        val result = executor(driver).tap(create, goesToPicker, retryOnUnverified = true)

        assertEquals(ActionMethod.GESTURE_ON_ELEMENT_BOUNDS, (result as ActionResult.Success).method)
        assertEquals(1, driver.clickLog.size)
        assertEquals(1, driver.gestureLog.size)
    }

    @Test
    fun aStaleObservationIsReplacedByAFreshOne() = runBlocking {
        val driver = world().apply { staleOnNextClick = true }
        val result = executor(driver).tap(create, goesToPicker)

        assertTrue(result is ActionResult.Success)
        assertEquals("the click was tried twice, once stale and once fresh", 2, driver.clickLog.size)
        assertEquals("picker", driver.current)
    }

    @Test
    fun givesUpWhenTheScreenKeepsChangingUnderTheTarget() = runBlocking {
        val driver = world().apply { accessibilityClick = DriverResult.STALE }
        val result = executor(driver).tap(create, goesToPicker)

        assertEquals(FailureReason.STALE_OBSERVATION, (result as ActionResult.Failed).reason)
        assertEquals("first try plus two retries", 3, driver.clickLog.size)
        assertEquals("picker is never reached", "feed", driver.current)
    }

    @Test
    fun aMissingTargetFailsAfterLookingAFewTimes() = runBlocking {
        val driver = world()
        val result = executor(driver).tap(TargetSpec("Share", texts = listOf("Share")), goesToPicker)

        assertEquals(FailureReason.TARGET_NOT_FOUND, (result as ActionResult.Failed).reason)
        assertEquals(3, driver.captureCount)
        assertTrue(driver.clickLog.isEmpty() && driver.gestureLog.isEmpty())
    }

    @Test
    fun twoEqualContactsAreAmbiguousAndNothingIsTapped() = runBlocking {
        val driver = world(
            extraNodes = listOf(fakeNode(text = "Rahim", clickable = true), fakeNode(text = "Rahim", clickable = true)),
        )
        val spec = TargetSpec("Rahim", texts = listOf("Rahim"), exact = true, requireUnique = true)
        val result = executor(driver).tap(spec, Expectation.ScreenChanged)

        assertEquals(FailureReason.TARGET_AMBIGUOUS, (result as ActionResult.Failed).reason)
        assertTrue(driver.clickLog.isEmpty() && driver.gestureLog.isEmpty())
    }

    @Test
    fun aDisabledTargetIsNeverTapped() = runBlocking {
        val driver = world(createEnabled = false)
        val result = executor(driver).tap(create, goesToPicker)

        assertEquals(FailureReason.TARGET_DISABLED, (result as ActionResult.Failed).reason)
        assertTrue(driver.clickLog.isEmpty() && driver.gestureLog.isEmpty())
    }

    @Test
    fun theWrongAppIsReportedAndNothingIsTapped() = runBlocking {
        val driver = world(packageName = "com.other.app")
        val result = executor(driver).tap(create, goesToPicker, expectedPackage = "com.example.app")

        assertEquals(FailureReason.WRONG_APP, (result as ActionResult.Failed).reason)
        assertTrue(driver.clickLog.isEmpty() && driver.gestureLog.isEmpty())
    }

    @Test
    fun aBlockerStopsEverythingBeforeAnyTap() = runBlocking {
        val driver = world()
        val result = executor(driver, blocker = Blocker.LOGIN).tap(create, goesToPicker)

        assertEquals(Blocker.LOGIN, (result as ActionResult.Blocked).blocker)
        assertTrue(driver.clickLog.isEmpty() && driver.gestureLog.isEmpty())
    }

    @Test
    fun aBlockerThatAppearsAfterTheTapIsReported() = runBlocking {
        val driver = world()
        // The blocker shows up only on the picker screen, that is, after the tap.
        val executor = UiActionExecutor(
            driver = driver,
            blockerCheck = { screen -> if (screen.containsText("New post")) Blocker.SECURITY_CHECK else null },
            timings = ActionTimings(resolveAttempts = 2, verifyAttempts = 2),
            pause = {},
        )
        val result = executor.tap(create, goesToPicker)

        assertEquals(Blocker.SECURITY_CHECK, (result as ActionResult.Blocked).blocker)
    }

    @Test
    fun noReadableScreenFailsCleanly() = runBlocking {
        val driver = world().apply { noScreen = true }
        val result = executor(driver).tap(create, goesToPicker)

        assertEquals(FailureReason.NO_SCREEN, (result as ActionResult.Failed).reason)
    }

    @Test
    fun aTapByPositionIsReportedAsAVisionTap() = runBlocking {
        val driver = world()
        // The Create node is the second one: its centre is at y = 120 + 50.
        val result = executor(driver).tapAt(540, 170, goesToPicker)

        assertEquals(ActionMethod.VISION_TAP, (result as ActionResult.Success).method)
        assertEquals("picker", driver.current)
    }

    @Test
    fun aTapOutsideTheScreenIsRefused() = runBlocking {
        val driver = world()
        val result = executor(driver).tapAt(5000, 170, goesToPicker)

        assertEquals(FailureReason.TARGET_OFFSCREEN, (result as ActionResult.Failed).reason)
        assertTrue(driver.gestureLog.isEmpty())
    }

    @Test
    fun anyOfAndAllOfCombineExpectations() = runBlocking {
        val before = ScreenObserver().observe(
            CapturedScreen(1, "p", "w", listOf(CapturedNode(0, text = "A", bounds = Bounds(0, 0, 10, 10))), 100, 100, 1),
        )
        val after = ScreenObserver().observe(
            CapturedScreen(2, "p", "w", listOf(CapturedNode(0, text = "B", bounds = Bounds(0, 0, 10, 10))), 100, 100, 1),
        )
        val executor = UiActionExecutor(driver = world(), pause = {})
        val hasB = Expectation.TextPresent("B")
        val hasC = Expectation.TextPresent("C")

        assertTrue(executor.matches(Expectation.AnyOf(listOf(hasC, hasB)), before, after))
        assertTrue(!executor.matches(Expectation.AllOf(listOf(hasC, hasB)), before, after))
        assertTrue(executor.matches(Expectation.AllOf(listOf(hasB, Expectation.ScreenChanged)), before, after))
    }
}
