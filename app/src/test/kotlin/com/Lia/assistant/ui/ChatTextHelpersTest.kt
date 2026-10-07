package com.Lia.assistant.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTextHelpersTest {
    @Test fun tokensKeepTrailingWhitespace() =
        assertEquals(listOf("Hello ", "big ", "world"), RevealText.tokens("Hello big world"))

    @Test fun joiningAllTokensRestoresTheText() {
        val text = "Line one\n\nLine two  with  gaps."
        assertEquals(text, RevealText.joined(RevealText.tokens(text), Int.MAX_VALUE))
    }

    @Test fun joinedShowsTheFirstNWords() {
        val tokens = RevealText.tokens("one two three")
        assertEquals("", RevealText.joined(tokens, 0))
        assertEquals("one ", RevealText.joined(tokens, 1))
        assertEquals("one two ", RevealText.joined(tokens, 2))
        assertEquals("one two three", RevealText.joined(tokens, 3))
        assertEquals("one two three", RevealText.joined(tokens, 99))
        assertEquals("", RevealText.joined(tokens, -5))
    }

    @Test fun emptyTextHasNoTokens() = assertTrue(RevealText.tokens("").isEmpty())

    @Test fun revealSpeedIsTwentyMillisecondsPerWord() = assertEquals(20L, RevealText.MS_PER_WORD)

    @Test fun speechTextDropsMarkdownSymbols() {
        assertEquals("Hello there x", SpeechText.clean("**Hello** _there_ `x`"))
        assertEquals("A list of things", SpeechText.clean("# A list\n> of   things"))
        assertEquals("", SpeechText.clean("***"))
    }
}
