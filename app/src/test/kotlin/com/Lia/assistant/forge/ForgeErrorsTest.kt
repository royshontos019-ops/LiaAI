package com.Lia.assistant.forge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeErrorsTest {
    @Test fun overloadedIsRecognisedByCodeOrStatus() {
        assertTrue(ForgeErrors.isOverloaded(503, null))
        assertTrue(ForgeErrors.isOverloaded(0, "UNAVAILABLE"))
        assertTrue(ForgeErrors.isOverloaded(200, "unavailable"))
        assertFalse(ForgeErrors.isOverloaded(429, "RESOURCE_EXHAUSTED"))
        assertFalse(ForgeErrors.isOverloaded(500, null))
    }

    @Test fun overloadedGetsAFriendlyMessage() {
        val text = ForgeErrors.friendly(503, "UNAVAILABLE", "The model is overloaded. Please try again later.")
        assertTrue(text, text.contains("busy"))
        assertFalse("the raw message must not be repeated", text.contains("overloaded"))
    }

    @Test fun otherErrorsAreMappedToo() {
        assertTrue(ForgeErrors.friendly(429, "RESOURCE_EXHAUSTED", null).contains("limit"))
        assertTrue(ForgeErrors.friendly(400, "INVALID_ARGUMENT", "API key not valid. Please pass a valid API key.").contains("API key"))
        assertTrue(ForgeErrors.friendly(403, "PERMISSION_DENIED", null).contains("refused"))
        assertTrue(ForgeErrors.friendly(401, null, null).contains("refused"))
        assertTrue(ForgeErrors.friendly(400, "INVALID_ARGUMENT", "bad field").contains("could not process"))
        assertTrue(ForgeErrors.friendly(404, "NOT_FOUND", null).contains("not available"))
        assertTrue(ForgeErrors.friendly(500, "INTERNAL", null).contains("their side") || ForgeErrors.friendly(500, "INTERNAL", null).contains("its side") || ForgeErrors.friendly(500, "INTERNAL", null).contains("side"))
        assertEquals("Gemini could not build the website (error 418).", ForgeErrors.friendly(418, null, null))
    }

    @Test fun friendlyMessagesNeverEchoTheRawText() {
        for (code in listOf(400, 401, 403, 404, 429, 500, 503, 418)) {
            val text = ForgeErrors.friendly(code, null, "secret-key-123 leaked in the message")
            assertFalse("code $code", text.contains("secret-key-123"))
        }
    }

    @Test fun sanitizeRemovesTheKeyAndCutsTheText() {
        assertEquals("bad *** here", ForgeErrors.sanitize("bad AIza123 here", "AIza123"))
        assertEquals(10, ForgeErrors.sanitize("x".repeat(500), "k", max = 10).length)
        assertEquals("", ForgeErrors.sanitize(null, "k"))
        assertEquals("keep", ForgeErrors.sanitize("keep", ""))
    }
}
