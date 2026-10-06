package com.Lia.assistant

object LiaCapabilityPrompts {
    @Suppress("UNUSED_PARAMETER")
    fun toolInstruction(assistantName: String): String =
        "You can open apps (open_app), start a call (call_contact) and prepare a text message " +
            "(message_contact). For messages the app opens with the text ready and the user taps Send " +
            "themselves, so say the draft is ready rather than that it was sent. You cannot read or " +
            "control the screen, and you cannot post to social apps; if asked, say so plainly and offer " +
            "what you can do instead. Never claim an action worked unless the tool result says so."
}
