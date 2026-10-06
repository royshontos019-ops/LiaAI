package com.Lia.assistant.voice

import android.content.Context

/**
 * STUB. The real live-voice session arrives in a later step.
 * PersonalityRepository already calls refreshInstructions() after every settings change.
 */
object VoiceSessionManager {
    /** In-memory only; wiped when the session stops. */
    val memory = ConversationMemory()

    @Suppress("UNUSED_PARAMETER")
    fun refreshInstructions(context: Context) = Unit

    fun stopSession() {
        memory.clear()
    }
}
