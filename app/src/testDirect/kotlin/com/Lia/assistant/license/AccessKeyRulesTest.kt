package com.Lia.assistant.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessKeyRulesTest {
    private val now = 1_800_000_000_000L
    private val day = AccessKeyRules.DAY_MS
    private val hour = 60L * 60 * 1000

    private fun decide(plan: String, expires: Long, blocked: Boolean = false, hasKey: Boolean = true) =
        AccessKeyRules.decide(plan, expires, blocked, now, hasKey)

    @Test fun noKeyIsNotActive() {
        val d = decide("monthly", now + day, hasKey = false)
        assertEquals(KeyStatus.NO_KEY, d.status)
        assertFalse(d.active)
    }

    @Test fun aFutureExpiryIsActive() {
        val d = decide("monthly", now + 12 * day)
        assertEquals(KeyStatus.ACTIVE, d.status)
        assertTrue(d.active)
        assertEquals("Active \u00B7 Monthly \u00B7 12 days left", d.statusText)
    }

    @Test fun theExpiryMomentItselfIsExpired() {
        assertEquals(KeyStatus.EXPIRED, decide("monthly", now).status)
        assertEquals(KeyStatus.ACTIVE, decide("monthly", now + 1).status)
        assertEquals(KeyStatus.EXPIRED, decide("monthly", now - 1).status)
    }

    @Test fun expiredIsNotActiveAndSaysSo() {
        val d = decide("trial", now - day)
        assertFalse(d.active)
        assertEquals("Expired \u00B7 Trial", d.statusText)
    }

    @Test fun blockedBeatsEverything() {
        assertEquals(KeyStatus.BLOCKED, decide("monthly", now + 30 * day, blocked = true).status)
        assertEquals(KeyStatus.BLOCKED, decide("lifetime", 0L, blocked = true).status)
        assertEquals(KeyStatus.BLOCKED, decide("", 0L, blocked = true).status)
        assertFalse(decide("lifetime", 0L, blocked = true).active)
    }

    @Test fun lifetimeNeverExpires() {
        for (expires in listOf(0L, -5L, now - 100 * day, now + day)) {
            val d = decide("lifetime", expires)
            assertTrue("expires=$expires", d.active)
            assertEquals("Active \u00B7 Lifetime", d.statusText)
        }
        assertTrue(decide("Lifetime", 0L).active)
        assertTrue(decide("  LIFETIME ", 0L).active)
    }

    @Test fun aDatedPlanWithoutAValidExpiryIsInvalid() {
        assertEquals(KeyStatus.INVALID, decide("monthly", 0L).status)
        assertEquals(KeyStatus.INVALID, decide("monthly", -1L).status)
    }

    @Test fun aKeyThatWasNeverVerifiedIsInvalid() {
        val d = decide("", now + day)
        assertEquals(KeyStatus.INVALID, d.status)
        assertFalse(d.active)
    }

    @Test fun timeLeftWording() {
        assertEquals("less than a day left", AccessKeyRules.timeLeftText(day - 1))
        assertEquals("less than a day left", AccessKeyRules.timeLeftText(0))
        assertEquals("1 day left", AccessKeyRules.timeLeftText(day))
        assertEquals("2 days left", AccessKeyRules.timeLeftText(day + 1))
        assertEquals("30 days left", AccessKeyRules.timeLeftText(30 * day))
    }

    @Test fun expiringSoonOnlyForDatedKeysEndingWithinTheWindow() {
        assertTrue(AccessKeyRules.expiringSoon("monthly", now + 2 * day, now))
        assertTrue(AccessKeyRules.expiringSoon("monthly", now + 3 * day, now))
        assertFalse(AccessKeyRules.expiringSoon("monthly", now + 3 * day + 1, now))
        assertFalse(AccessKeyRules.expiringSoon("monthly", now - 1, now))
        assertFalse(AccessKeyRules.expiringSoon("monthly", 0L, now))
        assertFalse(AccessKeyRules.expiringSoon("lifetime", now + day, now))
    }

    @Test fun aLiveCheckIsDueWhenNoneHappenedOrAfterThreeHours() {
        assertTrue(AccessKeyRules.needsLiveCheck(0L, now))
        assertFalse(AccessKeyRules.needsLiveCheck(now - 1, now))
        assertFalse(AccessKeyRules.needsLiveCheck(now - 3 * hour + 1, now))
        assertTrue(AccessKeyRules.needsLiveCheck(now - 3 * hour, now))
        assertTrue(AccessKeyRules.needsLiveCheck(now - 5 * hour, now))
    }

    @Test fun aClockSetBackwardsForcesACheck() {
        assertTrue(AccessKeyRules.needsLiveCheck(now + hour, now))
    }

    @Test fun keysAreTidiedAndChecked() {
        assertEquals("ABCD-1234-EFGH", AccessKeyRules.normalizeKey("  abcd-1234 efgh\n"))
        assertTrue(AccessKeyRules.looksLikeKey("ABCD-1234"))
        assertTrue(AccessKeyRules.looksLikeKey("A".repeat(64)))
        assertFalse(AccessKeyRules.looksLikeKey("SHORT"))
        assertFalse(AccessKeyRules.looksLikeKey("A".repeat(65)))
        assertFalse(AccessKeyRules.looksLikeKey("ABCD_1234!"))
        assertFalse(AccessKeyRules.looksLikeKey(""))
    }
}
