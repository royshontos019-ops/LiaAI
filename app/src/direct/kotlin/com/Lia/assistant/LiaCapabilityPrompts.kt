package com.Lia.assistant

object LiaCapabilityPrompts {
    fun toolInstruction(assistantName: String): String =
        "As $assistantName you can control the phone through the device_action tool " +
            "(back, home, recents). Use it only when the user asks, and confirm first before " +
            "anything that could lose data or send something."
}
