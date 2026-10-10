package com.Lia.assistant

import android.content.ContextWrapper
import com.Lia.assistant.license.AccessKeyManager
import com.Lia.assistant.license.LicenseBackend
import com.Lia.assistant.license.LicenseEngine
import com.Lia.assistant.license.LicenseStore
import com.Lia.assistant.license.RemoteResult
import com.Lia.assistant.license.StoredLicense
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryStore(var license: StoredLicense) : LicenseStore {
    override fun load() = license
    override fun save(license: StoredLicense) {
        this.license = license
    }
    override fun deviceId() = "device-1"
}

private object OfflineBackend : LicenseBackend {
    override val isConfigured = true
    override suspend fun activate(key: String, deviceId: String): RemoteResult = RemoteResult.Unreachable("offline")
    override suspend fun check(key: String, deviceId: String): RemoteResult = RemoteResult.Unreachable("offline")
}

class AccessKeyEnforcementTest {
    private val context = ContextWrapper(null)
    private val now = System.currentTimeMillis()
    private val day = 24L * 60 * 60 * 1000

    @After fun reset() {
        AccessKeyManager.configuredOverride = null
        AccessKeyManager.engine = null
    }

    private fun install(license: StoredLicense) {
        AccessKeyManager.configuredOverride = true
        AccessKeyManager.engine = LicenseEngine(MemoryStore(license), OfflineBackend)
    }

    private fun run(name: String, args: JSONObject = JSONObject()) =
        runBlocking { ActionExecutor.execute(context, name, args) }

    @Test fun anExpiredKeyRefusesEveryPhoneTool() {
        install(StoredLicense("ABCD-1234", "monthly", now - day, false, now))
        assertFalse(AccessKeyManager.isActive(context))
        for (tool in listOf("open_app", "call_contact", "read_screen", "tap_text", "go_home", "fly_to_moon")) {
            val reply = run(tool, JSONObject().put("app_name", "x").put("text", "y"))
            assertEquals(tool, "access_key_inactive", reply.getString("result"))
            assertTrue(tool, reply.getString("error").contains("access key"))
        }
    }

    @Test fun aBlockedKeyRefusesAndTheMessageSaysWhatToDo() {
        install(StoredLicense("ABCD-1234", "lifetime", 0L, blocked = true, lastRemoteCheck = now))
        val reply = run("open_app", JSONObject().put("app_name", "x"))
        assertEquals("access_key_inactive", reply.getString("result"))
        assertTrue(reply.getString("error").contains("Blocked"))
        assertTrue(reply.getString("error").contains("Profile"))
    }

    @Test fun noKeyAtAllRefusesWhenLicensingIsConfigured() {
        install(StoredLicense())
        assertEquals("access_key_inactive", run("go_home").getString("result"))
    }

    @Test fun anActiveKeyLetsToolsThrough() {
        install(StoredLicense("ABCD-1234", "monthly", now + 30 * day, false, now))
        assertTrue(AccessKeyManager.isActive(context))
        assertEquals("Unknown tool: fly_to_moon", run("fly_to_moon").getString("error"))
    }

    @Test fun aLifetimeKeyLetsToolsThrough() {
        install(StoredLicense("ABCD-1234", "lifetime", 0L, false, now))
        assertEquals("Unknown tool: fly_to_moon", run("fly_to_moon").getString("error"))
    }

    @Test fun withoutConfigurationNothingIsRefused() {
        AccessKeyManager.configuredOverride = false
        assertTrue(AccessKeyManager.isActive(context))
        assertEquals("Unknown tool: fly_to_moon", run("fly_to_moon").getString("error"))
    }

    @Test fun theBuiltInSettingsAreEmptyWithoutALicensingFile() {
        // This test build has no app/licensing.json, so the real gate is open and says so.
        assertTrue(AccessKeyManager.isActive(context))
    }
}
