package com.Lia.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMemoryTest {
    @Test fun emptyRecapIsNull() = assertNull(ConversationMemory().recap())

    @Test fun recapFormat() {
        val m = ConversationMemory()
        m.add(Role.USER, "hello")
        m.add(Role.ASSISTANT, "hi there")
        assertEquals("User: hello\nYou: hi there", m.recap())
    }

    @Test fun blankTurnsAreIgnored() {
        val m = ConversationMemory()
        m.add(Role.USER, "   ")
        assertEquals(0, m.size)
    }

    @Test fun keepsOnlyLast12Turns() {
        val m = ConversationMemory()
        repeat(20) { m.add(Role.USER, "msg$it") }
        assertEquals(12, m.size)
        val recap = m.recap()!!
        assertTrue(recap.startsWith("User: msg8"))
        assertTrue(recap.endsWith("msg19"))
    }

    @Test fun recapIsCappedAndStartsOnWholeLine() {
        val m = ConversationMemory()
        repeat(12) { m.add(Role.USER, "x".repeat(300)) }
        val recap = m.recap()!!
        assertTrue(recap.length <= ConversationMemory.MAX_CHARS)
        assertTrue(recap.startsWith("User: "))
    }

    @Test fun clearEmptiesMemory() {
        val m = ConversationMemory()
        m.add(Role.USER, "hi")
        m.clear()
        assertNull(m.recap())
    }
}
