package com.Lia.assistant

import com.Lia.assistant.action.PhoneToolDeclarations
import com.Lia.assistant.action.declaredToolNames
import org.json.JSONArray

/** Play flavor: exactly three tools. */
object LiaToolCatalog {
    fun declarations(): JSONArray {
        val out = JSONArray()
        PhoneToolDeclarations.base(autoSend = false).forEach { out.put(it) }
        return out
    }

    fun toolNames(): List<String> = declarations().declaredToolNames()
}
