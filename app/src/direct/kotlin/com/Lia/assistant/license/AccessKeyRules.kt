package com.Lia.assistant.license

enum class KeyStatus(val active: Boolean) {
    NO_KEY(false),
    BLOCKED(false),
    INVALID(false),
    EXPIRED(false),
    ACTIVE(true),
}

data class KeyDecision(val status: KeyStatus, val statusText: String) {
    val active: Boolean get() = status.active
}

/** The licence rules. Pure Kotlin: no Android, no clock of its own, so every case can be tested. */
object AccessKeyRules {
    const val LIFETIME = "lifetime"
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val LIVE_CHECK_INTERVAL_MS = 3L * 60 * 60 * 1000

    /** After a failed attempt, wait this long before trying again. */
    const val RETRY_AFTER_FAILURE_MS = 60_000L

    /**
     * Blocked beats everything. A lifetime plan never expires. Any other plan needs an expiry in the
     * future. A key that has not been verified (no plan yet) is not active.
     */
    fun decide(plan: String, expiresAt: Long, blocked: Boolean, now: Long, hasKey: Boolean = true): KeyDecision {
        val p = plan.trim().lowercase()
        if (!hasKey) return KeyDecision(KeyStatus.NO_KEY, "No access key")
        if (blocked) return KeyDecision(KeyStatus.BLOCKED, "Blocked by the administrator")
        if (p.isEmpty()) return KeyDecision(KeyStatus.INVALID, "This key has not been verified yet")
        val name = p.replaceFirstChar { it.uppercase() }
        if (p == LIFETIME) return KeyDecision(KeyStatus.ACTIVE, "Active \u00B7 Lifetime")
        if (expiresAt <= 0L) return KeyDecision(KeyStatus.INVALID, "This key has no valid expiry date")
        if (now >= expiresAt) return KeyDecision(KeyStatus.EXPIRED, "Expired \u00B7 $name")
        return KeyDecision(KeyStatus.ACTIVE, "Active \u00B7 $name \u00B7 ${timeLeftText(expiresAt - now)}")
    }

    fun timeLeftText(remainingMs: Long): String {
        if (remainingMs < DAY_MS) return "less than a day left"
        val days = (remainingMs + DAY_MS - 1) / DAY_MS
        return if (days == 1L) "1 day left" else "$days days left"
    }

    /** True for an active, dated key that ends within [windowDays]. */
    fun expiringSoon(plan: String, expiresAt: Long, now: Long, windowDays: Int = 3): Boolean {
        if (plan.trim().lowercase() == LIFETIME) return false
        val remaining = expiresAt - now
        return expiresAt > 0 && remaining > 0 && remaining <= windowDays * DAY_MS
    }

    /** A check is due when none happened yet, when the clock went backwards, or after 3 hours. */
    fun needsLiveCheck(lastRemoteCheck: Long, now: Long): Boolean =
        lastRemoteCheck <= 0L || lastRemoteCheck > now || now - lastRemoteCheck >= LIVE_CHECK_INTERVAL_MS

    fun normalizeKey(raw: String): String = raw.trim().uppercase().filter { !it.isWhitespace() }

    fun looksLikeKey(key: String): Boolean =
        key.length in 8..64 && key.all { it in 'A'..'Z' || it in '0'..'9' || it == '-' }
}
