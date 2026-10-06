package com.Lia.assistant

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.Lia.assistant.action.NovaAccessibilityService

object AccessKeyManager {
    fun isEnabled(context: Context): Boolean = NovaAccessibilityService.isEnabled(context)

    fun openSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
