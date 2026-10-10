package com.Lia.assistant.license

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeStore(var license: StoredLicense = StoredLicense()) : LicenseStore {
    override fun load() = license
    override fun save(license: StoredLicense) {
        this.license = license
    }
    override fun deviceId() = "device-1"
}

private class FakeBackend(var configured: Boolean = true) : LicenseBackend {
    override val isConfigured get() = configured
    var next: RemoteResult = RemoteResult.Unreachable("offline")
    val calls = mutableListOf<String>()

    override suspend fun activate(key: String, deviceId: String): RemoteResult {
        calls += "activate:$key:$deviceId"
        return next
    }

    override suspend fun check(key: String, deviceId: String): RemoteResult {
        calls += "check:$key:$deviceId"
        return next
    }
}

class LicenseEngineTest {
    private val hour = 60L * 60 * 1000
    private val day = AccessKeyRules.DAY_MS
    private var now = 1_800_000_000_000L

    private val store = FakeStore()
    private val backend = FakeBackend()
    private val engine = LicenseEngine(store, backend) { now }

    private fun remembered(plan: String = "monthly", expiresIn: Long = 30 * day, lastCheck: Long = 0L, blocked: Boolean = false) {
        store.license = StoredLicense("ABCD-1234", plan, now + expiresIn, blocked, lastCheck)
    }

    // ---- not configured -----------------------------------------------------------------

    @Test fun withoutConfigurationNothingIsEnforcedAndItIsReported() = runTest {
        backend.configured = false
        assertTrue(engine.isActive())
        assertEquals("Licensing not configured", engine.info().statusText)
        assertTrue(engine.info().active)
        assertEquals(ActivationResult.NotConfigured, engine.activate("ABCD-1234"))
        assertEquals(LiveCheckResult.NotConfigured, engine.liveCheck(force = true))
        assertTrue("the backend was never called", backend.calls.isEmpty())
    }

    // ---- activation ---------------------------------------------------------------------

    @Test fun aValidKeyIsActivatedStoredAndBoundToTheDevice() = runTest {
        backend.next = RemoteResult.Valid("Monthly", now + 30 * day)
        val result = engine.activate("  abcd-1234 ")

        assertTrue(result is ActivationResult.Activated)
        assertEquals(listOf("activate:ABCD-1234:device-1"), backend.calls)
        assertEquals(StoredLicense("ABCD-1234", "monthly", now + 30 * day, false, now), store.license)
        assertTrue(engine.isActive())
        assertTrue(engine.info().hasRememberedKey)
        assertEquals("Active \u00B7 Monthly \u00B7 30 days left", engine.info().statusText)
    }

    @Test fun aBadlyShapedKeyNeverReachesTheServer() = runTest {
        val result = engine.activate("nope!")
        assertTrue(result is ActivationResult.InvalidFormat)
        assertTrue(backend.calls.isEmpty())
        assertEquals(StoredLicense(), store.license)
    }

    @Test fun anUnknownKeyIsRejectedAndNotRemembered() = runTest {
        backend.next = RemoteResult.Rejected("unknown_key", "That key is not valid.")
        val result = engine.activate("ABCD-1234") as ActivationResult.Rejected
        assertEquals("unknown_key", result.code)
        assertEquals(StoredLicense(), store.license)
        assertFalse(engine.isActive())
    }

    @Test fun activationNeedsTheServerAndFailsClosedOffline() = runTest {
        backend.next = RemoteResult.Unreachable("timeout")
        assertTrue(engine.activate("ABCD-1234") is ActivationResult.NetworkError)
        assertEquals(StoredLicense(), store.license)
        assertFalse(engine.isActive())
    }

    @Test fun aBlockedKeyIsRejectedAtActivationAndStaysInactive() = runTest {
        backend.next = RemoteResult.Blocked
        val result = engine.activate("ABCD-1234") as ActivationResult.Rejected
        assertEquals("blocked", result.code)
        assertTrue(store.license.blocked)
        assertFalse(engine.isActive())
        assertTrue(engine.info().blocked)
    }

    @Test fun aKeyThatIsAlreadyExpiredIsNotActivated() = runTest {
        backend.next = RemoteResult.Valid("trial", now - day)
        assertTrue(engine.activate("ABCD-1234") is ActivationResult.Rejected)
        assertFalse(engine.isActive())
    }

    // ---- live check ---------------------------------------------------------------------

    @Test fun noRememberedKeyMeansNothingToCheck() = runTest {
        assertEquals(LiveCheckResult.NoKey, engine.liveCheck())
        assertTrue(backend.calls.isEmpty())
    }

    @Test fun aLiveCheckHappensAtMostOnceEveryThreeHours() = runTest {
        remembered(lastCheck = now - 4 * hour)
        backend.next = RemoteResult.Valid("monthly", now + 30 * day)

        assertTrue(engine.liveCheck() is LiveCheckResult.Checked)
        assertEquals(1, backend.calls.size)

        now += 2 * hour + 59 * 60 * 1000
        assertEquals(LiveCheckResult.Skipped, engine.liveCheck())
        assertEquals(1, backend.calls.size)

        now += 2 * 60 * 1000 // now more than three hours after the last real answer
        assertTrue(engine.liveCheck() is LiveCheckResult.Checked)
        assertEquals(2, backend.calls.size)
    }

    @Test fun forceChecksRightAway() = runTest {
        remembered(lastCheck = now - 1000)
        backend.next = RemoteResult.Valid("monthly", now + 30 * day)
        assertTrue(engine.liveCheck(force = true) is LiveCheckResult.Checked)
        assertEquals(1, backend.calls.size)
    }

    @Test fun aNetworkErrorFailsOpenAndKeepsEverythingAsItWas() = runTest {
        remembered(lastCheck = now - 4 * hour)
        val before = store.license
        backend.next = RemoteResult.Unreachable("timeout")

        assertTrue(engine.liveCheck() is LiveCheckResult.FailedOpen)
        assertEquals(before, store.license)
        assertTrue("still active", engine.isActive())
    }

    @Test fun afterAFailureTheNextAttemptWaitsAMinute() = runTest {
        remembered(lastCheck = now - 4 * hour)
        backend.next = RemoteResult.Unreachable("timeout")
        engine.liveCheck()
        assertEquals(1, backend.calls.size)

        now += 30_000
        assertEquals(LiveCheckResult.Skipped, engine.liveCheck())
        assertEquals(1, backend.calls.size)

        now += 31_000
        engine.liveCheck()
        assertEquals(2, backend.calls.size)
    }

    @Test fun anAdminBlockMakesTheKeyInactiveAtOnce() = runTest {
        remembered(lastCheck = now - 4 * hour)
        assertTrue(engine.isActive())
        backend.next = RemoteResult.Blocked

        val result = engine.liveCheck() as LiveCheckResult.Checked
        assertFalse(result.info.active)
        assertTrue(result.info.blocked)
        assertFalse(engine.isActive())
        assertEquals("Blocked by the administrator", engine.info().statusText)
        assertEquals("the key is kept so it can be unblocked later", "ABCD-1234", store.license.key)
    }

    @Test fun anUnblockedKeyComesBackAtTheNextCheck() = runTest {
        remembered(lastCheck = now - 4 * hour, blocked = true)
        assertFalse(engine.isActive())
        backend.next = RemoteResult.Valid("monthly", now + 30 * day)

        engine.liveCheck()
        assertTrue(engine.isActive())
        assertFalse(engine.info().blocked)
    }

    @Test fun aKeyTheServerNoLongerKnowsIsForgotten() = runTest {
        remembered(lastCheck = now - 4 * hour)
        backend.next = RemoteResult.Rejected("unknown_key", "That key is not valid.")

        val result = engine.liveCheck() as LiveCheckResult.Cleared
        assertEquals("That key is not valid.", result.message)
        assertFalse(engine.info().hasRememberedKey)
        assertFalse(engine.isActive())
    }

    @Test fun anExpiryIsEnforcedLocallyEvenWithoutAnyNetwork() = runTest {
        remembered(expiresIn = day, lastCheck = now)
        backend.next = RemoteResult.Unreachable("offline")
        assertTrue(engine.isActive())

        now += day + 1
        assertFalse("expired while offline", engine.isActive())
        assertEquals(KeyStatus.EXPIRED.name, AccessKeyRules.decide(store.license.plan, store.license.expiresAt, false, now).status.name)
    }

    @Test fun aClockSetBackwardsStillTriggersACheck() = runTest {
        remembered(lastCheck = now + 5 * hour)
        backend.next = RemoteResult.Valid("monthly", now + 30 * day)
        assertTrue(engine.liveCheck() is LiveCheckResult.Checked)
        assertEquals(1, backend.calls.size)
    }

    @Test fun theDeviceIdTravelsWithEveryCheck() = runTest {
        remembered(lastCheck = 0L)
        backend.next = RemoteResult.Valid("monthly", now + day)
        engine.liveCheck()
        assertEquals("check:ABCD-1234:device-1", backend.calls.single())
    }
}
