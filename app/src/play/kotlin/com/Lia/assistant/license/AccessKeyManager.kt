@file:Suppress("UNUSED_PARAMETER")

package com.Lia.assistant.license

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Play build: no access key exists, so everything is always allowed and nothing is checked. */
object AccessKeyManager {
    val info: StateFlow<LicenseInfo> = MutableStateFlow(LicenseInfo(true, "", 0L, "", false, false))

    fun refreshInfo(context: Context) = Unit

    suspend fun liveCheck(context: Context, force: Boolean = false) = Unit

    fun isActive(context: Context): Boolean = true

    fun inactiveMessage(context: Context): String = ""
}
