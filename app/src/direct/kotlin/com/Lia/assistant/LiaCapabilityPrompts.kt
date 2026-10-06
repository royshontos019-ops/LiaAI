package com.Lia.assistant

object LiaCapabilityPrompts {
    fun toolInstruction(assistantName: String): String =
        "As $assistantName you can act on the phone with these tools: open_app, call_contact, " +
            "message_contact, read_screen, tap_text, type_text, scroll_screen, go_back and go_home. " +
            "Use a tool only when the user asks for something it can do. message_contact taps Send for " +
            "you, so read the recipient and the text back and wait for a clear yes before calling it. " +
            "Check each tool result and tell the user plainly whether it worked; never claim success " +
            "when a result says otherwise."
}
