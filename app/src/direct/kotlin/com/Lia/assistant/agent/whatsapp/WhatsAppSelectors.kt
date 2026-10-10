package com.Lia.assistant.agent.whatsapp

import com.Lia.assistant.agent.core.ScreenRegion
import com.Lia.assistant.agent.core.TargetSpec

/** WhatsApp's controls. Like the social selectors, adjust here if the app's screens change. */
object WhatsAppSelectors {
    const val PACKAGE = "com.whatsapp"

    val send = TargetSpec(
        label = "Send button",
        texts = listOf("Send"),
        exact = true,
        viewIdParts = listOf(":id/send"),
        mustBeClickable = true,
    )
    val chatInput = TargetSpec(
        label = "message box",
        viewIdParts = listOf(":id/entry"),
        classNameParts = listOf("EditText"),
        mustBeEditable = true,
    )
    val search = TargetSpec(
        label = "Search button",
        texts = listOf("Search"),
        exact = true,
        viewIdParts = listOf("menuitem_search", "search_icon"),
    )
    val searchField = TargetSpec(
        label = "search field",
        viewIdParts = listOf("search_input"),
        classNameParts = listOf("EditText"),
        mustBeEditable = true,
    )
    val moreOptions = TargetSpec(
        label = "More options",
        texts = listOf("More options"),
        exact = true,
        region = ScreenRegion.TOP,
    )
    val mute = TargetSpec(label = "Mute notifications", texts = listOf("Mute notifications"), exact = true)
    val unmute = TargetSpec(label = "Unmute notifications", texts = listOf("Unmute notifications"), exact = true)
    val muteAlways = TargetSpec(label = "Always", texts = listOf("Always"), exact = true)
    val ok = TargetSpec(label = "OK button", texts = listOf("OK"), exact = true)

    /** The name part of a row in a chat or search result list. */
    const val CONTACT_ROW_ID = "conversations_row_contact_name"

    /** The text part of a message bubble. */
    const val MESSAGE_TEXT_ID = "message_text"

    val notOnWhatsApp = listOf("isn't on WhatsApp", "is not on WhatsApp", "phone number shared via url is invalid")

    fun contactRow(name: String) = TargetSpec(
        label = "chat with $name",
        texts = listOf(name),
        exact = true,
        viewIdParts = listOf(CONTACT_ROW_ID),
    )
}
