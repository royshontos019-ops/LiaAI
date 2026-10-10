package com.Lia.assistant.license

/** Play build: the same shape as the direct one so shared code compiles. There is nothing to check here. */
data class LicenseInfo(
    val active: Boolean,
    val plan: String,
    val expiresAt: Long,
    val statusText: String,
    val blocked: Boolean,
    val hasRememberedKey: Boolean,
)
