package com.Lia.assistant

import com.Lia.assistant.action.PhoneToolDeclarations
import com.Lia.assistant.action.declaredToolNames
import com.Lia.assistant.forge.ForgeTool
import org.json.JSONArray

/** Play flavor: exactly three tools. */
object LiaToolCatalog {
    fun declarations(): JSONArray {
        val out = JSONArray()
        PhoneToolDeclarations.base(autoSend = false).forEach { out.put(it) }
        out.put(ForgeTool.declaration())
        return out
    }

    fun toolNames(): List<String> = declarations().declaredToolNames()
}
