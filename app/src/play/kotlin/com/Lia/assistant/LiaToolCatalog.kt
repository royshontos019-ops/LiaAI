package com.Lia.assistant

import org.json.JSONArray

/** Play build: no tools at all (screen control is removed at compile time). */
object LiaToolCatalog {
    fun tools(): List<ToolSpec> = emptyList()

    fun declarations(): JSONArray = JSONArray()
}
