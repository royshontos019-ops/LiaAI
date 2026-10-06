package com.Lia.assistant.action

import org.json.JSONArray
import org.json.JSONObject

/**
 * One Gemini function declaration. Every parameter has type STRING; the parameters block is
 * type OBJECT and is left out entirely when the tool takes no parameters.
 * [params] maps parameter name -> description; [required] lists the mandatory names.
 */
fun functionDecl(
    name: String,
    description: String,
    params: Map<String, String> = emptyMap(),
    required: List<String> = emptyList(),
): JSONObject {
    val decl = JSONObject().put("name", name).put("description", description)
    if (params.isNotEmpty()) {
        val props = JSONObject()
        for ((paramName, paramDescription) in params) {
            props.put(paramName, JSONObject().put("type", "STRING").put("description", paramDescription))
        }
        val schema = JSONObject().put("type", "OBJECT").put("properties", props)
        val req = JSONArray()
        required.filter { it in params }.forEach { req.put(it) }
        if (req.length() > 0) schema.put("required", req)
        decl.put("parameters", schema)
    }
    return decl
}

/** The three tools both flavors offer. */
object PhoneToolDeclarations {
    fun base(autoSend: Boolean): List<JSONObject> = listOf(
        functionDecl(
            "open_app",
            "Open an installed app by the name shown on the home screen.",
            mapOf("app_name" to "The app's name, for example WhatsApp or YouTube"),
            listOf("app_name"),
        ),
        functionDecl(
            "call_contact",
            "Call a contact by name, or a phone number.",
            mapOf("contact" to "A contact name or a phone number"),
            listOf("contact"),
        ),
        functionDecl(
            "message_contact",
            if (autoSend) {
                "Write a text message to a contact by name or phone number, and tap Send for the user. " +
                    "Only call this after the user has confirmed the recipient and the text."
            } else {
                "Prepare a text message to a contact by name or phone number. The message app opens " +
                    "with the text filled in and the user taps Send themselves."
            },
            mapOf(
                "contact" to "A contact name or a phone number",
                "message" to "The text to send",
                "app" to "sms or whatsapp (default sms)",
            ),
            listOf("contact", "message"),
        ),
    )
}

/** Small helpers so every tool answers with the same JSON shapes. */
object ToolJson {
    fun result(value: String, vararg extra: Pair<String, Any>): JSONObject {
        val o = JSONObject().put("result", value)
        for ((k, v) in extra) o.put(k, v)
        return o
    }

    fun error(message: String): JSONObject = JSONObject().put("error", message)

    fun missing(param: String): JSONObject = error("Missing parameter: $param")

    fun unknownTool(name: String): JSONObject = error("Unknown tool: $name")
}

/** Reads the names back out of a declarations array (used by tests and logging-free diagnostics). */
fun JSONArray.declaredToolNames(): List<String> = (0 until length()).map { getJSONObject(it).getString("name") }
