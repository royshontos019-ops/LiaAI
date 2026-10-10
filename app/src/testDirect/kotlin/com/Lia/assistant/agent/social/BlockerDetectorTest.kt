package com.Lia.assistant.agent.social

import com.Lia.assistant.agent.core.Blocker
import com.Lia.assistant.agent.core.Bounds
import com.Lia.assistant.agent.core.ScreenObservation
import com.Lia.assistant.agent.core.UiElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockerDetectorTest {
    private val detector = BlockerDetector()

    private fun el(id: Int, text: String, password: Boolean = false) = UiElement(
        id, text, "", "", "android.widget.TextView", Bounds(0, 0, 100, 100),
        clickable = false, enabled = true, editable = password, scrollable = false,
        focused = false, password = password, depth = 1,
    )

    private fun screen(pkg: String, vararg elements: UiElement) =
        ScreenObservation(1, pkg, "W", elements.toList(), 1080, 2400, 1)

    @Test fun aPasswordFieldMeansLogin() =
        assertEquals(Blocker.LOGIN, detector.detect(screen("com.instagram.android", el(0, "Log in"), el(1, "", password = true))))

    @Test fun loginWordsWithoutAPasswordFieldAlsoCount() =
        assertEquals(
            Blocker.LOGIN,
            detector.detect(screen("com.instagram.android", el(0, "Log into Instagram"), el(1, "Create new account"))),
        )

    @Test fun captcha() =
        assertEquals(Blocker.CAPTCHA, detector.detect(screen("com.facebook.katana", el(0, "Please confirm: I'm not a robot"))))

    @Test fun twoFactor() =
        assertEquals(Blocker.TWO_FACTOR, detector.detect(screen("com.instagram.android", el(0, "Enter the 6-digit code we sent you"))))

    @Test fun securityCheck() =
        assertEquals(Blocker.SECURITY_CHECK, detector.detect(screen("com.facebook.katana", el(0, "We detected unusual activity on your account"))))

    @Test fun permissionPrompt() =
        assertEquals(
            Blocker.PERMISSION_PROMPT,
            detector.detect(screen("com.google.android.permissioncontroller", el(0, "Allow Instagram to access photos?"), el(1, "Allow"))),
        )

    @Test fun anOrdinaryScreenIsNotABlocker() =
        assertNull(detector.detect(screen("com.instagram.android", el(0, "Share"), el(1, "Write a caption..."))))

    @Test fun whatIsTypedInAPasswordFieldIsNeverRead() {
        // Even if a password field's text said "captcha", only the field itself counts: it is a login.
        assertEquals(
            Blocker.LOGIN,
            detector.detect(screen("com.instagram.android", el(0, "captcha", password = true))),
        )
    }
}
