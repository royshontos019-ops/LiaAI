package com.Lia.assistant.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenObserverTest {

    private fun capture(vararg nodes: CapturedNode, id: Long = 7L) = CapturedScreen(
        captureId = id,
        packageName = "com.example.app",
        windowClass = "Main",
        nodes = nodes.toList(),
        screenWidth = 1080,
        screenHeight = 2400,
        windowStamp = 99L,
    )

    private val box = Bounds(0, 0, 100, 100)

    @Test
    fun observationCarriesTheCaptureFacts() {
        val observation = ScreenObserver().observe(capture(CapturedNode(0, text = "Hi", bounds = box), id = 12L))
        assertEquals(12L, observation.observationId)
        assertEquals("com.example.app", observation.packageName)
        assertEquals("Main", observation.windowClass)
        assertEquals(1080, observation.screenWidth)
        assertEquals(2400, observation.screenHeight)
        assertEquals(99L, observation.windowStamp)
    }

    @Test
    fun dropsInvisibleEmptyBoundsAndUselessNodes() {
        val observation = ScreenObserver().observe(
            capture(
                CapturedNode(0, text = "kept", bounds = box),
                CapturedNode(1, text = "hidden", bounds = box, visibleToUser = false),
                CapturedNode(2, text = "no size", bounds = Bounds(0, 0, 0, 0)),
                CapturedNode(3, bounds = box), // nothing to read, nothing to tap
                CapturedNode(4, bounds = box, clickable = true),
                CapturedNode(5, viewId = "x:id/title", bounds = box),
            ),
        )
        assertEquals(listOf(0, 4, 5), observation.elements.map { it.id })
    }

    @Test
    fun keepsTheOriginalIdsSoTheDriverCanFindTheNode() {
        val observation = ScreenObserver().observe(
            capture(
                CapturedNode(0, bounds = box),
                CapturedNode(1, bounds = box),
                CapturedNode(2, text = "third", bounds = box),
            ),
        )
        assertEquals(2, observation.elements.single().id)
        assertEquals("third", observation.element(2)?.text)
        assertNull(observation.element(0))
    }

    @Test
    fun neverKeepsWhatIsTypedInAPasswordField() {
        val observation = ScreenObserver().observe(
            capture(CapturedNode(0, text = "hunter2", bounds = box, editable = true, password = true)),
        )
        val field = observation.elements.single()
        assertEquals("", field.text)
        assertTrue(field.password)
        assertFalse(observation.containsText("hunter2"))
    }

    @Test
    fun longTextIsShortened() {
        val long = "a".repeat(5000)
        val observation = ScreenObserver(maxTextLength = 50).observe(capture(CapturedNode(0, text = long, bounds = box)))
        assertEquals(50, observation.elements.single().text.length)
    }

    @Test
    fun labelFallsBackToTheContentDescription() {
        val observation = ScreenObserver().observe(
            capture(
                CapturedNode(0, text = "Text", contentDescription = "Desc", bounds = box),
                CapturedNode(1, contentDescription = "Only desc", bounds = box),
            ),
        )
        assertEquals("Text", observation.element(0)?.label)
        assertEquals("Only desc", observation.element(1)?.label)
    }

    @Test
    fun signatureChangesWhenTheScreenChanges() {
        val observer = ScreenObserver()
        val a = observer.observe(capture(CapturedNode(0, text = "One", bounds = box)))
        val same = observer.observe(capture(CapturedNode(0, text = "One", bounds = box), id = 8L))
        val different = observer.observe(capture(CapturedNode(0, text = "Two", bounds = box)))
        assertEquals(a.signature, same.signature)
        assertFalse(a.signature == different.signature)
    }
}
