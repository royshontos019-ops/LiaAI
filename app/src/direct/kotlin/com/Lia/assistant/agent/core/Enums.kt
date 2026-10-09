package com.Lia.assistant.agent.core

/** The stages of one single action (a tap, a typing step). */
enum class ExecPhase { OBSERVE, RESOLVE, VALIDATE, EXECUTE, WAIT, VERIFY }

/** How an action was actually performed, from most to least precise. */
enum class ActionMethod { ACCESSIBILITY_CLICK, GESTURE_ON_ELEMENT_BOUNDS, VISION_TAP, SET_TEXT }

/** Things the agent must never get past by itself. It stops and asks the user. */
enum class Blocker { LOGIN, CAPTCHA, TWO_FACTOR, SECURITY_CHECK, PERMISSION_PROMPT }

enum class FailureReason {
    NO_SCREEN,
    WRONG_APP,
    TARGET_NOT_FOUND,
    TARGET_AMBIGUOUS,
    TARGET_DISABLED,
    TARGET_OFFSCREEN,
    STALE_OBSERVATION,
    ACTION_FAILED,
    UNSUPPORTED,
    VERIFICATION_FAILED,
    TEXT_NOT_ENTERED,
    PASSWORD_FIELD,
    ;

    /** Short lowercase code for tool results, for example "target_not_found". */
    val code: String get() = name.lowercase()
}

/** What the device said when asked to do something. */
enum class DriverResult { OK, FAILED, STALE, NOT_FOUND, UNSUPPORTED }

enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }
