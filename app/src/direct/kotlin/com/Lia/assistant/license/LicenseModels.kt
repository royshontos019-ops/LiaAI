package com.Lia.assistant.license

data class LicenseInfo(
    val active: Boolean,
    val plan: String,
    val expiresAt: Long,
    val statusText: String,
    val blocked: Boolean,
    val hasRememberedKey: Boolean,
) {
    companion object {
        /** Shown before anything was loaded. Active, so nothing flashes a warning. */
        val UNKNOWN = LicenseInfo(true, "", 0L, "", false, false)

        /** No backend settings were built in. Nothing is enforced, and the app says so. */
        val NOT_CONFIGURED = LicenseInfo(true, "", 0L, "Licensing not configured", false, false)
    }
}

/** What is kept on the phone. [active] is not stored here: it is worked out from the rest. */
data class StoredLicense(
    val key: String = "",
    val plan: String = "",
    val expiresAt: Long = 0L,
    val blocked: Boolean = false,
    val lastRemoteCheck: Long = 0L,
)

/** What the backend said. Anything that is not a clear answer is [Unreachable], and that fails open. */
sealed interface RemoteResult {
    data class Valid(val plan: String, val expiresAt: Long) : RemoteResult
    object Blocked : RemoteResult
    data class Rejected(val code: String, val message: String) : RemoteResult
    data class Unreachable(val reason: String) : RemoteResult
    object NotConfigured : RemoteResult
}

sealed interface ActivationResult {
    data class Activated(val info: LicenseInfo) : ActivationResult
    data class Rejected(val code: String, val message: String) : ActivationResult
    data class NetworkError(val message: String) : ActivationResult
    data class InvalidFormat(val message: String) : ActivationResult
    object NotConfigured : ActivationResult
}

sealed interface LiveCheckResult {
    object Skipped : LiveCheckResult
    object NotConfigured : LiveCheckResult
    object NoKey : LiveCheckResult
    data class Checked(val info: LicenseInfo) : LiveCheckResult
    data class Cleared(val message: String) : LiveCheckResult
    data class FailedOpen(val reason: String) : LiveCheckResult
}
