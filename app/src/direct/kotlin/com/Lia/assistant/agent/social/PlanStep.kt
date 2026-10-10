package com.Lia.assistant.agent.social

import com.Lia.assistant.agent.core.Expectation
import com.Lia.assistant.agent.core.TargetSpec

/** One thing the task does. [Publish] is the only step that cannot be undone. */
sealed interface PlanStep {
    val label: String

    /** Opens the app. With [mediaUri] the app opens straight into its share screen with that file. */
    data class OpenApp(override val label: String, val packageName: String, val mediaUri: String?) : PlanStep

    /** Taps [spec] and checks [expect]. When [skipIfPresent] is already on screen the step is skipped. */
    data class Tap(
        override val label: String,
        val spec: TargetSpec,
        val expect: Expectation,
        val skipIfPresent: TargetSpec? = null,
    ) : PlanStep

    data class TypeText(override val label: String, val field: TargetSpec, val text: String) : PlanStep

    data class Back(override val label: String) : PlanStep

    /** The irreversible tap. It only ever runs after the user has confirmed. */
    data class Publish(override val label: String, val spec: TargetSpec, val expect: Expectation) : PlanStep
}

/**
 * Builds the steps for a request. In DRAFT mode the plan has no [PlanStep.Publish] at all, so a
 * draft cannot post by construction. The runner also refuses a Publish step in DRAFT mode.
 */
open class SocialPlanner {
    open fun plan(request: SocialTaskRequest): List<PlanStep> {
        val platform = request.platform
        val s = SocialSelectors.forPlatform(platform)
        val steps = ArrayList<PlanStep>()

        steps += PlanStep.OpenApp("Open ${platform.displayName}", platform.packageName, request.mediaUri)

        fun nextSteps(count: Int) = repeat(count) {
            steps += PlanStep.Tap("Next", s.next, Expectation.ScreenChanged, skipIfPresent = s.captionField)
        }

        when (request.action) {
            SocialAction.TEXT_POST ->
                steps += PlanStep.Tap("Open the composer", s.composeEntry, Expectation.TargetPresent(s.captionField))
            SocialAction.CREATE_POST -> if (platform == Platform.INSTAGRAM) nextSteps(2)
            SocialAction.CREATE_REEL -> nextSteps(2)
            SocialAction.CREATE_STORY -> Unit
        }

        if (request.caption.isNotEmpty() && request.action != SocialAction.CREATE_STORY) {
            steps += PlanStep.TypeText("Type the caption", s.captionField, request.caption)
        }

        if (request.mode == TaskMode.DRAFT) {
            steps += PlanStep.Back("Leave the composer")
            steps += PlanStep.Tap(
                "Save as draft",
                s.saveDraft,
                Expectation.AnyOf(listOf(Expectation.TargetGone(s.saveDraft), Expectation.TextPresent("saved"))),
            )
        } else if (request.action == SocialAction.CREATE_STORY) {
            steps += PlanStep.Publish("Share to story", s.shareStory, s.storyPublished)
        } else {
            steps += PlanStep.Publish("Share the post", s.share, s.published)
        }
        return steps
    }
}
