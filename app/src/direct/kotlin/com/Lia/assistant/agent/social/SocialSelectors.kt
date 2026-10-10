package com.Lia.assistant.agent.social

import com.Lia.assistant.agent.core.Expectation
import com.Lia.assistant.agent.core.ScreenRegion
import com.Lia.assistant.agent.core.TargetSpec

/**
 * The controls of one app's posting screens, written as [TargetSpec]s (text, view id, class hints).
 * These are best guesses about the apps' current screens. Apps change their layout often, so if a
 * step stops finding its target on a real phone, this is the one file to adjust.
 */
class PlatformSelectors(
    val next: TargetSpec,
    val captionField: TargetSpec,
    val share: TargetSpec,
    val shareStory: TargetSpec,
    val saveDraft: TargetSpec,
    val composeEntry: TargetSpec,
    /** What the screen shows once the app has really started posting. */
    val published: Expectation,
    val storyPublished: Expectation,
)

object SocialSelectors {

    fun forPlatform(platform: Platform): PlatformSelectors = when (platform) {
        Platform.INSTAGRAM -> INSTAGRAM
        Platform.FACEBOOK -> FACEBOOK
    }

    private val INSTAGRAM = PlatformSelectors(
        next = TargetSpec(
            label = "Next button",
            texts = listOf("Next"),
            exact = true,
            viewIdParts = listOf("next_button_textview", "action_bar_button_text"),
            region = ScreenRegion.TOP,
        ),
        captionField = TargetSpec(
            label = "caption field",
            viewIdParts = listOf("caption_text_view", "caption_input", "caption_edit"),
            classNameParts = listOf("EditText"),
            mustBeEditable = true,
        ),
        share = TargetSpec(
            label = "Share button",
            texts = listOf("Share"),
            exact = true,
            viewIdParts = listOf("share_footer_button", "action_bar_button_text"),
        ),
        shareStory = TargetSpec(
            label = "Your story button",
            texts = listOf("Your story"),
            viewIdParts = listOf("share_to_story", "your_story"),
        ),
        saveDraft = TargetSpec(label = "Save draft button", texts = listOf("Save draft"), exact = true),
        composeEntry = TargetSpec(
            label = "create button",
            texts = listOf("Create", "New post"),
            viewIdParts = listOf("creation_tab"),
        ),
        published = Expectation.AnyOf(
            listOf(Expectation.TextPresent("Sharing"), Expectation.TextPresent("Shared"), Expectation.TextPresent("Posting")),
        ),
        storyPublished = Expectation.AnyOf(
            listOf(Expectation.TextPresent("Sharing"), Expectation.TextPresent("Your story")),
        ),
    )

    private val FACEBOOK = PlatformSelectors(
        next = TargetSpec(label = "Next button", texts = listOf("Next"), exact = true, region = ScreenRegion.TOP),
        captionField = TargetSpec(
            label = "caption field",
            texts = listOf("What's on your mind", "Say something about this"),
            viewIdParts = listOf("composer_text", "status_text"),
            classNameParts = listOf("EditText"),
            mustBeEditable = true,
        ),
        share = TargetSpec(label = "Post button", texts = listOf("Post", "Share"), exact = true),
        shareStory = TargetSpec(label = "Share to story button", texts = listOf("Share to story", "Share")),
        saveDraft = TargetSpec(label = "Save draft button", texts = listOf("Save draft"), exact = true),
        composeEntry = TargetSpec(
            label = "What's on your mind box",
            texts = listOf("What's on your mind"),
            viewIdParts = listOf("composer_entry"),
        ),
        published = Expectation.AnyOf(
            listOf(Expectation.TextPresent("Posting"), Expectation.TextPresent("Posted"), Expectation.TextPresent("Uploading")),
        ),
        storyPublished = Expectation.AnyOf(
            listOf(Expectation.TextPresent("Sharing"), Expectation.TextPresent("Shared"), Expectation.TextPresent("Posting")),
        ),
    )
}
