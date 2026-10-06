package com.Lia.assistant

import android.content.Context

/** Play build: no package enumeration at all (QUERY_ALL_PACKAGES is removed). */
@Suppress("UNUSED_PARAMETER")
object InstalledAppLabelCache {
    val size: Int = 0
    fun prewarm(context: Context) = Unit
    fun invalidate() = Unit
    fun packageFor(label: String): String? = null
}
