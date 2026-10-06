package com.Lia.assistant

import org.json.JSONObject

/** Shared result type returned by every tool/action, in every flavor. */
data class ToolResult(val ok: Boolean, val message: String, val data: JSONObject? = null) {
    companion object {
        fun unavailable(what: String) = ToolResult(false, "$what is unavailable in this build.")
    }
}

/** Shared description of a tool exposed to the model. */
data class ToolSpec(val name: String, val description: String, val parametersSchema: JSONObject)
