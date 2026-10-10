package com.Lia.assistant.forge

import java.io.File

enum class ForgePhase { IDLE, BUILDING, DONE, FAILED, CANCELLED }

data class ForgeMetrics(
    val chars: Int = 0,
    val sections: Int = 0,
    val elapsedSec: Int = 0,
    val tokensPerSec: Float = 0f,
)

/** Everything the screen needs. [logSeq] grows with every log line so the UI can tell a new line arrived. */
data class ForgeUiState(
    val phase: ForgePhase = ForgePhase.IDLE,
    val prompt: String = "",
    val isEdit: Boolean = false,
    val stage: ForgeStage = ForgeStage.PLANNING,
    val progress: Float = 0f,
    val metrics: ForgeMetrics = ForgeMetrics(),
    val sections: List<String> = emptyList(),
    val log: List<String> = emptyList(),
    val logSeq: Long = 0L,
    val buildId: Long = 0L,
    val file: File? = null,
    val error: String? = null,
)

/** The answer to start/edit/retry. [message] is written so it can be spoken to the user as it is. */
sealed class StartResult(val message: String) {
    object Started : StartResult("Okay, I'm building your website. Open Lia to watch it come together.")
    object MissingKey : StartResult("I need your Gemini API key first. Please add it in Settings.")
    object Offline : StartResult("I can't reach the internet right now, so I can't build a website.")
    object Busy : StartResult("I'm already building a website. Let it finish, or cancel it first.")
    object NothingToEdit : StartResult("There's no website to change yet. Ask me to build one first.")
    object NoPrompt : StartResult("Tell me what the website is for, and I'll build it.")

    val started: Boolean get() = this === Started
}

/** The two texts sent to Gemini. */
data class ForgeRequestText(val system: String, val user: String) {
    fun withHint(hint: String) = copy(user = user + "\n\n" + hint)
}

/** How one streamed call ended. The text itself arrives through the callback. */
sealed interface StreamResult {
    data class Completed(val finishReason: String?) : StreamResult
    data class HttpError(val code: Int, val status: String?, val message: String?) : StreamResult
    object Network : StreamResult
    object NoModel : StreamResult
}

sealed interface BuildOutcome {
    data class Success(val html: String) : BuildOutcome
    data class Failure(val message: String) : BuildOutcome
}
