package com.Lia.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ForgeIntentTest {
    private fun matches(text: String) = assertNotNull("should match: $text", ForgeIntent.extract(text))
    private fun rejects(text: String) = assertNull("should NOT match: $text", ForgeIntent.extract(text))

    @Test fun buildRequestsMatch() {
        matches("build me a website")
        matches("Can you create a website for my bakery?")
        matches("make a landing page for my startup")
        matches("please design a web site for my gym")
        matches("I want you to build a portfolio website")
    }

    @Test fun hinglishAndBanglaMatch() {
        matches("mere liye ek website banao")
        matches("ek website bana do")
        matches("আমার জন্য একটা ওয়েবসাইট বানাও")
    }

    @Test fun questionsAndLearningDoNotMatch() {
        rejects("how do I build a website")
        rejects("what is a website")
        rejects("is it hard to make a website")
        rejects("explain how to create a website")
        rejects("I want to learn to build a website")
    }

    @Test fun talkingAboutAnExistingWebsiteDoesNotMatch() {
        rejects("my website is slow")
        rejects("I made a website yesterday")
        rejects("write a blog post for my website")
        rejects("design a logo for my website")
        rejects("tell me about websites")
    }

    @Test fun unrelatedAndEmptyDoNotMatch() {
        rejects("build me an app")
        rejects("")
        rejects("   ")
    }

    @Test fun topicIsTheTextAfterForAboutOrCalled() {
        assertEquals(
            "my bakery called Sweet Crumbs",
            ForgeIntent.extract("Can you create a website for my bakery called Sweet Crumbs?")?.topic,
        )
        assertEquals("dogs", ForgeIntent.extract("make a website about dogs.")?.topic)
        assertNull(ForgeIntent.extract("build me a website")?.topic)
    }

    @Test fun promptKeepsTheOriginalText() =
        assertEquals("Build me a Website!", ForgeIntent.extract("  Build me a Website!  ")?.prompt)
}
