package com.Lia.assistant.agent.social

sealed interface ParseResult {
    data class Valid(val request: SocialTaskRequest) : ParseResult
    data class Invalid(val code: String, val message: String) : ParseResult
}

/**
 * A checked request. [parse] is the only way to make one from the model's arguments, and it runs
 * BEFORE any task exists, so a bad request never reaches the screen.
 */
data class SocialTaskRequest(
    val platform: Platform,
    val action: SocialAction,
    val mediaUri: String?,
    val caption: String,
    val mode: TaskMode,
    val targetAccount: String?,
) {
    companion object {
        /** A missing [mode] means DRAFT, the safe choice. */
        fun parse(args: Map<String, String?>, media: MediaResolver): ParseResult {
            fun arg(name: String): String = args[name]?.trim().orEmpty()

            val platformText = arg("platform")
            if (platformText.isEmpty()) return invalid("missing_platform", "Which platform: instagram or facebook?")
            val platform = Platform.fromCode(platformText)
                ?: return invalid("unknown_platform", "Platform must be instagram or facebook.")

            val actionText = arg("action")
            if (actionText.isEmpty()) return invalid("missing_action", "Which action: create_post, create_reel, create_story or text_post?")
            val action = SocialAction.fromCode(actionText)
                ?: return invalid("unknown_action", "Action must be create_post, create_reel, create_story or text_post.")

            if (platform == Platform.INSTAGRAM && action == SocialAction.TEXT_POST) {
                return invalid("unsupported_combination", "Instagram has no text-only posts. Use Facebook, or share a photo.")
            }

            val modeText = arg("mode")
            val mode = if (modeText.isEmpty()) TaskMode.DRAFT
            else TaskMode.fromCode(modeText) ?: return invalid("unknown_mode", "Mode must be publish or draft.")
            if (mode == TaskMode.DRAFT && !action.allowsDraft) {
                return invalid("draft_not_supported", "Stories cannot be saved as a draft. Use publish, or choose another action.")
            }

            val caption = arg("caption")
            if (caption.length > platform.maxCaption) {
                return invalid("caption_too_long", "${platform.displayName} captions can be at most ${platform.maxCaption} characters.")
            }
            if (action == SocialAction.TEXT_POST && caption.isEmpty()) {
                return invalid("caption_required", "A text post needs the text in caption.")
            }
            if (action == SocialAction.CREATE_STORY && caption.isNotEmpty()) {
                return invalid("caption_not_supported", "Stories are posted without a caption.")
            }

            val mediaText = arg("media_uri")
            val mediaUri: String? = when {
                action.needsMedia -> when (val r = media.resolve(mediaText)) {
                    is MediaResolution.Allowed -> r.uri
                    is MediaResolution.Rejected -> return invalid(r.code, r.message)
                }
                mediaText.isNotEmpty() -> return invalid("media_not_allowed", "A text post takes no media.")
                else -> null
            }

            val account = arg("target_account").ifEmpty { null }
            return ParseResult.Valid(SocialTaskRequest(platform, action, mediaUri, caption, mode, account))
        }

        private fun invalid(code: String, message: String) = ParseResult.Invalid(code, message)
    }
}
