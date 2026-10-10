package com.Lia.assistant.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseResponseTest {
    private fun parse(code: Int, body: String) = LicenseResponse.parse(code, body)

    @Test fun okCarriesThePlanAndExpiry() =
        assertEquals(
            RemoteResult.Valid("monthly", 1_900_000_000_000L),
            parse(200, """{"status":"ok","plan":"Monthly","expiresAt":1900000000000}"""),
        )

    @Test fun blocked() = assertEquals(RemoteResult.Blocked, parse(200, """{"status":"blocked"}"""))

    @Test fun unknownKeyAndDeviceMismatchAreRejections() {
        val unknown = parse(200, """{"status":"unknown_key"}""") as RemoteResult.Rejected
        assertEquals("unknown_key", unknown.code)
        assertTrue(unknown.message.isNotBlank())
        val other = parse(200, """{"status":"device_mismatch","message":"Used elsewhere"}""") as RemoteResult.Rejected
        assertEquals("device_mismatch", other.code)
        assertEquals("Used elsewhere", other.message)
    }

    @Test fun anythingUnclearFailsOpenAsUnreachable() {
        val cases = listOf(
            parse(500, """{"status":"blocked"}"""),
            parse(403, "forbidden"),
            parse(200, "<html>oops</html>"),
            parse(200, ""),
            parse(200, """{"status":"ok"}"""),
            parse(200, """{"status":"something_new"}"""),
            parse(200, """{}"""),
        )
        for (c in cases) assertTrue("$c", c is RemoteResult.Unreachable)
    }

    @Test fun aBlockedAnswerOnAServerErrorDoesNotBlock() =
        assertFalse(parse(502, """{"status":"blocked"}""") == RemoteResult.Blocked)

    @Test fun configNeedsAnHttpsAddress() {
        assertFalse(LicenseConfig("", "").isConfigured)
        assertFalse(LicenseConfig("http://example.com/x", "").isConfigured)
        assertFalse(LicenseConfig("https://", "").isConfigured)
        assertTrue(LicenseConfig("https://example.com/x", "").isConfigured)
    }
}
