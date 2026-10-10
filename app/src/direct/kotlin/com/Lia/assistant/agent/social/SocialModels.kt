package com.Lia.assistant.agent.social

import com.Lia.assistant.agent.core.Blocker
import com.Lia.assistant.agent.core.FailureReason
import com.Lia.assistant.agent.core.TaskState

enum class Platform(val code: String, val packageName: String, val displayName: String, val maxCaption: Int) {
    INSTAGRAM("instagram", "com.instagram.android", "Instagram", 2200),
    FACEBOOK("facebook", "com.facebook.katana", "Facebook", 60000),
    ;

    companion object {
        fun fromCode(code: String): Platform? = entries.firstOrNull { it.code == code.trim().lowercase() }
    }
}

enum class SocialAction(val code: String, val needsMedia: Boolean, val allowsDraft: Boolean) {
    CREATE_POST("create_post", needsMedia = true, allowsDraft = true),
    CREATE_REEL("create_reel", needsMedia = true, allowsDraft = true),
    CREATE_STORY("create_story", needsMedia = true, allowsDraft = false),
    TEXT_POST("text_post", needsMedia = false, allowsDraft = true),
    ;

    companion object {
        fun fromCode(code: String): SocialAction? = entries.firstOrNull { it.code == code.trim().lowercase() }
    }
}

/** PUBLISH posts after the user confirms. DRAFT only ever saves a draft and never posts. */
enum class TaskMode {
    PUBLISH,
    DRAFT,
    ;

    companion object {
        fun fromCode(code: String): TaskMode? = entries.firstOrNull { it.name.equals(code.trim(), ignoreCase = true) }
    }
}

enum class TaskStatus {
    QUEUED,
    RUNNING,
    WAITING_FOR_CONFIRMATION,
    WAITING_FOR_USER,
    COMPLETED,
    DRAFT_SAVED,

    /** The Share tap was made, but the screen never showed that the post went through. */
    SUBMITTED_UNVERIFIED,
    FAILED,
    CANCELLED,
    ;

    val isFinished: Boolean
        get() = this == COMPLETED || this == DRAFT_SAVED || this == SUBMITTED_UNVERIFIED ||
            this == FAILED || this == CANCELLED
}

enum class ControlCommand {
    CONFIRM,
    REJECT,
    RESUME,
    CANCEL,
    STATUS,
    ;

    companion object {
        fun fromCode(code: String): ControlCommand? = entries.firstOrNull { it.name.equals(code.trim(), ignoreCase = true) }
    }
}

/** What a task tells the outside world. [message] is written so the assistant can say it to the user. */
data class TaskReport(
    val taskId: String,
    val status: TaskStatus,
    /** Short lowercase machine code, for example "waiting_for_confirmation" or "confirm_not_pending". */
    val code: String,
    val message: String,
    val state: TaskState,
    val step: Int,
    val totalSteps: Int,
    /** False when a control command was refused. The task itself is unchanged then. */
    val accepted: Boolean = true,
    val needsConfirmation: Boolean = false,
    val blocker: Blocker? = null,
    val failure: FailureReason? = null,
)
