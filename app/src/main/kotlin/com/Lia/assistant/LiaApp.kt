package com.Lia.assistant

import android.app.Application
import com.Lia.assistant.data.NovaPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LiaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Load nova_prefs from disk off the main thread so later reads/writes never block it.
        CoroutineScope(Dispatchers.IO).launch { NovaPreferences.warmUp(this@LiaApp) }
    }
}
