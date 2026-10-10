package com.Lia.assistant.license

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface LicenseStore {
    fun load(): StoredLicense
    fun save(license: StoredLicense)
    fun deviceId(): String
}

interface LicenseBackend {
    val isConfigured: Boolean
    suspend fun activate(key: String, deviceId: String): RemoteResult
    suspend fun check(key: String, deviceId: String): RemoteResult
}

/**
 * All licence behaviour with no Android in it: it talks to a [LicenseStore] and a [LicenseBackend]
 * and reads time from [clock]. AccessKeyManager is a thin Android shell around this.
 */
class LicenseEngine(
    private val store: LicenseStore,
    private val backend: LicenseBackend,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val lock = Mutex()

    @Volatile private var lastAttemptAt = 0L

    fun info(): LicenseInfo {
        if (!backend.isConfigured) return LicenseInfo.NOT_CONFIGURED
        val s = store.load()
        val d = AccessKeyRules.decide(s.plan, s.expiresAt, s.blocked, clock(), hasKey = s.key.isNotEmpty())
        return LicenseInfo(d.active, s.plan, s.expiresAt, d.statusText, s.blocked, s.key.isNotEmpty())
    }

    /** Read fresh every time, so an expiry takes effect without waiting for any network check. */
    fun isActive(): Boolean = !backend.isConfigured || info().active

    /** Needs the server. Without it nothing is activated. */
    suspend fun activate(rawKey: String): ActivationResult = lock.withLock {
        if (!backend.isConfigured) return ActivationResult.NotConfigured
        val key = AccessKeyRules.normalizeKey(rawKey)
        if (!AccessKeyRules.looksLikeKey(key)) {
            return ActivationResult.InvalidFormat("That does not look like an access key. Check it and try again.")
        }
        val now = clock()
        when (val r = backend.activate(key, store.deviceId())) {
            is RemoteResult.Valid -> {
                store.save(StoredLicense(key, r.plan.trim().lowercase(), r.expiresAt, blocked = false, lastRemoteCheck = now))
                val info = info()
                if (info.active) ActivationResult.Activated(info) else ActivationResult.Rejected("inactive", info.statusText)
            }
            RemoteResult.Blocked -> {
                store.save(StoredLicense(key = key, blocked = true, lastRemoteCheck = now))
                ActivationResult.Rejected("blocked", "This key has been blocked.")
            }
            is RemoteResult.Rejected -> ActivationResult.Rejected(r.code, r.message)
            is RemoteResult.Unreachable ->
                ActivationResult.NetworkError("Could not reach the licence server. Check the internet and try again.")
            RemoteResult.NotConfigured -> ActivationResult.NotConfigured
        }
    }

    /**
     * Asks the server at most once per 3 hours (and not more than once a minute after a failure).
     * A network or server problem FAILS OPEN: the stored state stays as it was. A "blocked" answer
     * makes the key inactive at once.
     */
    suspend fun liveCheck(force: Boolean = false): LiveCheckResult = lock.withLock {
        if (!backend.isConfigured) return LiveCheckResult.NotConfigured
        val stored = store.load()
        if (stored.key.isEmpty()) return LiveCheckResult.NoKey
        val now = clock()
        if (!force) {
            if (!AccessKeyRules.needsLiveCheck(stored.lastRemoteCheck, now)) return LiveCheckResult.Skipped
            val sinceAttempt = now - lastAttemptAt
            if (lastAttemptAt > 0 && sinceAttempt in 0 until AccessKeyRules.RETRY_AFTER_FAILURE_MS) {
                return LiveCheckResult.Skipped
            }
        }
        lastAttemptAt = now
        when (val r = backend.check(stored.key, store.deviceId())) {
            is RemoteResult.Valid -> {
                store.save(stored.copy(plan = r.plan.trim().lowercase(), expiresAt = r.expiresAt, blocked = false, lastRemoteCheck = now))
                LiveCheckResult.Checked(info())
            }
            RemoteResult.Blocked -> {
                store.save(stored.copy(blocked = true, lastRemoteCheck = now))
                LiveCheckResult.Checked(info())
            }
            is RemoteResult.Rejected -> {
                store.save(StoredLicense())
                LiveCheckResult.Cleared(r.message)
            }
            is RemoteResult.Unreachable -> LiveCheckResult.FailedOpen(r.reason)
            RemoteResult.NotConfigured -> LiveCheckResult.NotConfigured
        }
    }
}
