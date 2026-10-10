package com.Lia.assistant

import android.content.Context
import com.Lia.assistant.forge.ForgeController
import org.json.JSONObject

/**
 * The entry point the voice tool and the chat use. It starts the Website Forge and returns at once;
 * the building goes on in the background. Both flavors share this.
 */
object Forge {
    /** True when a build started. */
    fun start(context: Context, args: JSONObject): Boolean =
        startForTool(context, args).optString("result") == "forge_started"

    /** The answer for the `build_website` tool. The model reads "error" aloud when it did not start. */
    fun startForTool(context: Context, args: JSONObject): JSONObject {
        // The tool sends "prompt"; the typed chat sends "request".
        val prompt = args.optString("prompt").ifBlank { args.optString("request") }
        val result = ForgeController.start(context, prompt)
        return if (result.started) {
            JSONObject().put("result", "forge_started")
        } else {
            JSONObject().put("result", "forge_unavailable").put("error", result.message)
        }
    }
}
