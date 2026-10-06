package com.Lia.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallDeduplicatorTest {
    @Test fun repeatIsSkipped() {
        val d = ToolCallDeduplicator()
        assertTrue(d.shouldHandle("call-1"))
        assertFalse(d.shouldHandle("call-1"))
        assertTrue(d.shouldHandle("call-2"))
        assertFalse(d.shouldHandle("call-2"))
    }

    @Test fun isBounded_oldestIdIsForgotten() {
        val d = ToolCallDeduplicator(capacity = 3)
        listOf("a", "b", "c", "d").forEach { assertTrue(d.shouldHandle(it)) }
        assertEquals(3, d.size)
        assertTrue("a was evicted, so it counts as new again", d.shouldHandle("a"))
        assertFalse("d is still remembered", d.shouldHandle("d"))
    }

    @Test fun blankIdsAreAlwaysHandledAndNeverStored() {
        val d = ToolCallDeduplicator()
        assertTrue(d.shouldHandle(""))
        assertTrue(d.shouldHandle(""))
        assertTrue(d.shouldHandle("  "))
        assertEquals(0, d.size)
    }

    @Test fun clearForgetsEverything() {
        val d = ToolCallDeduplicator()
        d.shouldHandle("x")
        d.clear()
        assertTrue(d.shouldHandle("x"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun capacityMustBePositive() {
        ToolCallDeduplicator(capacity = 0)
    }
}
