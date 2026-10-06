package com.Lia.assistant

object LiaCapabilityPrompts {
    @Suppress("UNUSED_PARAMETER")
    fun toolInstruction(assistantName: String): String =
        "You cannot control the phone screen or other apps in this version. If the user asks for " +
            "that, say so briefly and offer what you can do by voice instead."
}
