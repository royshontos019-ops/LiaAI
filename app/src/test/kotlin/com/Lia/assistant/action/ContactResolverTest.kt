package com.Lia.assistant.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactResolverTest {
    private fun c(name: String, number: String = "123456") = ContactCandidate(name, number)

    @Test fun phoneNumberDetection() {
        assertTrue(ContactResolver.looksLikePhoneNumber("+880 1712-345678"))
        assertTrue(ContactResolver.looksLikePhoneNumber("(555) 123-4567"))
        assertTrue(ContactResolver.looksLikePhoneNumber("555.123.4567"))
        assertTrue(ContactResolver.looksLikePhoneNumber("12345"))
        assertFalse("only four digits", ContactResolver.looksLikePhoneNumber("1234"))
        assertFalse("has letters", ContactResolver.looksLikePhoneNumber("John 12345"))
        assertFalse(ContactResolver.looksLikePhoneNumber("ext 12345"))
        assertFalse(ContactResolver.looksLikePhoneNumber(""))
        assertFalse(ContactResolver.looksLikePhoneNumber("   "))
    }

    @Test fun normalizeKeepsDigitsAndLeadingPlus() {
        assertEquals("+8801712345678", ContactResolver.normalizeNumber("+880 1712-345678"))
        assertEquals("5551234567", ContactResolver.normalizeNumber("(555) 123-4567"))
    }

    @Test fun aSpokenNumberNeedsNoLookup() {
        val found = ContactResolver.directNumber(" +1 (555) 123-4567 ")
        assertNotNull(found)
        assertEquals("+15551234567", found!!.number)
        assertNull(ContactResolver.directNumber("Mom"))
    }

    @Test fun exactBeatsPrefixBeatsContains() {
        val list = listOf(c("Big John"), c("Johnny"), c("John"), c("Johnson"))
        assertEquals("John", ContactResolver.rank("john", list)?.name)
        assertEquals("John", ContactResolver.rank("joh", list)?.name)            // prefix tier; shortest name first
        assertEquals("Big John", ContactResolver.rank("big", list)?.name)
    }

    @Test fun containsIsTheLastResort() {
        val list = listOf(c("Big John"), c("Mr Johnston"))
        assertEquals("Big John", ContactResolver.rank("ohn", list)?.name)        // shorter of the two
    }

    @Test fun rankingIgnoresCase() =
        assertEquals("MOM", ContactResolver.rank("mom", listOf(c("Momo"), c("MOM")))?.name)

    @Test fun rankingNeedsARealMatch() {
        assertNull(ContactResolver.rank("zed", listOf(c("Alice"), c("Bob"))))
        assertNull(ContactResolver.rank("zed", emptyList()))
        assertNull(ContactResolver.rank("  ", listOf(c("Alice"))))
    }
}
