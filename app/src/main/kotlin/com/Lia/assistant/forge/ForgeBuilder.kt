package com.Lia.assistant.forge

import kotlinx.coroutines.delay

/** One call to the model. [onText] gets each new piece of text as it arrives. */
interface SiteStreamer {
    suspend fun stream(apiKey: String, request: ForgeRequestText, onText: (String) -> Unit): StreamResult
}

/**
 * Turns the model's answer into a usable page, with the retries that make this reliable:
 * - overloaded (503 / UNAVAILABLE): wait 4 s and try once more;
 * - too short, or no </html> at the end: try once more with a hint;
 * - still cut off: accept it only if it is at least 14000 characters (repaired), else fail.
 */
class ForgeBuilder(
    private val streamer: SiteStreamer,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    companion object {
        const val OVERLOAD_WAIT_MS = 4_000L
        const val MIN_CHARS = 1_500
        const val ACCEPT_TRUNCATED_CHARS = 14_000
    }

    /** [onPartial] gets the cleaned text so far. It starts again from nothing on the retry. */
    suspend fun build(apiKey: String, request: ForgeRequestText, onPartial: (String) -> Unit): BuildOutcome {
        var current = request
        var overloadRetried = false
        var hintRetried = false
        var best = ""

        while (true) {
            val buffer = StringBuilder()
            val result = streamer.stream(apiKey, current) { piece ->
                buffer.append(piece)
                onPartial(ForgeHtml.clean(buffer.toString()))
            }
            when (result) {
                is StreamResult.HttpError -> {
                    if (!overloadRetried && ForgeErrors.isOverloaded(result.code, result.status)) {
                        overloadRetried = true
                        pause(OVERLOAD_WAIT_MS)
                        continue
                    }
                    return BuildOutcome.Failure(ForgeErrors.friendly(result.code, result.status, result.message))
                }
                StreamResult.Network -> return BuildOutcome.Failure(ForgeErrors.NO_CONNECTION)
                StreamResult.NoModel -> return BuildOutcome.Failure(ForgeErrors.NO_MODEL)
                is StreamResult.Completed -> {
                    val html = ForgeHtml.clean(buffer.toString())
                    if (html.length > best.length) best = html
                    val usable = html.length >= MIN_CHARS && ForgeHtml.looksLikeHtml(html) && ForgeHtml.isComplete(html)
                    if (usable) return BuildOutcome.Success(html)

                    if (!hintRetried) {
                        hintRetried = true
                        current = request.withHint(ForgeSystemPrompt.RETRY_HINT)
                        continue
                    }
                    if (best.length >= ACCEPT_TRUNCATED_CHARS && ForgeHtml.looksLikeHtml(best)) {
                        return BuildOutcome.Success(ForgeHtml.repairTruncated(best))
                    }
                    return BuildOutcome.Failure(ForgeErrors.STOPPED_EARLY)
                }
            }
        }
    }
}
