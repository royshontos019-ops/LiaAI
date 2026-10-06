package com.Lia.assistant

import org.json.JSONArray
import org.json.JSONObject

/** Shared result type returned by every tool/action, in every flavor. */
data class ToolResult(val ok: Boolean, val message: String, val data: JSONObject? = null) {
    companion object {
        fun unavailable(what: String) = ToolResult(false, "$what is unavailable in this build.")
    }
}

/** One tool parameter. Gemini Live parameters are always declared as type STRING. */
data class ToolParam(val name: String, val description: String, val required: Boolean = true)

/** Flavor-independent description of a tool exposed to the model. */
data class ToolSpec(val name: String, val description: String, val params: List<ToolParam> = emptyList())

/** Turns [ToolSpec]s into the Gemini Live `functionDeclarations` JSON array. */
object ToolDeclarations {
    fun from(tools: List<ToolSpec>): JSONArray {
        val out = JSONArray()
        for (tool in tools) {
            val decl = JSONObject().put("name", tool.name).put("description", tool.description)
            if (tool.params.isNotEmpty()) {
                val props = JSONObject()
                val required = JSONArray()
                for (p in tool.params) {
                    props.put(p.name, JSONObject().put("type", "STRING").put("description", p.description))
                    if (p.required) required.put(p.name)
                }
                val schema = JSONObject().put("type", "OBJECT").put("properties", props)
                if (required.length() > 0) schema.put("required", required)
                decl.put("parameters", schema)
            }
            out.put(decl)
        }
        return out
    }
}
