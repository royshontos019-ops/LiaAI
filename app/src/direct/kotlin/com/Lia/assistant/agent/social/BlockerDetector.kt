package com.Lia.assistant.agent.social

import com.Lia.assistant.agent.core.Blocker
import com.Lia.assistant.agent.core.ScreenObservation

/**
 * Spots screens the agent must never get past alone. It only reads labels; it never reads what is
 * typed in a password field.
 */
class BlockerDetector {

    fun detect(observation: ScreenObservation): Blocker? {
        val pkg = observation.packageName.lowercase()
        val text = observation.elements
            .filter { !it.password }
            .joinToString("\n") { "${it.text}\n${it.contentDescription}" }
            .lowercase()

        fun has(vararg parts: String) = parts.any { text.contains(it) }

        if ((pkg.contains("permissioncontroller") || pkg.contains("packageinstaller")) &&
            has("allow", "while using the app", "only this time")
        ) {
            return Blocker.PERMISSION_PROMPT
        }
        if (has("captcha", "i'm not a robot", "select all images", "verify you're human", "verify you are human")) {
            return Blocker.CAPTCHA
        }
        if (has(
                "two-factor", "2-factor", "security code", "confirmation code", "login code",
                "6-digit code", "enter the code",
            )
        ) {
            return Blocker.TWO_FACTOR
        }
        if (has(
                "suspicious", "confirm it's you", "confirm it\u2019s you", "unusual activity",
                "verify your identity", "temporarily locked", "help us confirm",
            )
        ) {
            return Blocker.SECURITY_CHECK
        }
        val passwordField = observation.elements.any { it.password }
        if (passwordField || (has("log in", "log into") && has("create new account", "forgot password", "forgotten password"))) {
            return Blocker.LOGIN
        }
        return null
    }
}
