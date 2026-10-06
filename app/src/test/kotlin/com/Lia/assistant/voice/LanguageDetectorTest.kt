package com.Lia.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageDetectorTest {
    @Test fun devanagari_isHindi() {
        assertEquals(DetectedLanguage.HINDI, LanguageDetector.detect("तुम कैसे हो"))
        assertEquals(DetectedLanguage.HINDI, LanguageDetector.detect("open WhatsApp खोलो"))
    }

    @Test fun shortHinglish() =
        assertEquals(DetectedLanguage.HINGLISH, LanguageDetector.detect("kya haal hai yaar"))

    @Test fun longerHinglish() =
        assertEquals(DetectedLanguage.HINGLISH, LanguageDetector.detect("bhai mujhe kal ka weather batao please"))

    @Test fun oneHitInShortSentence_usesRatio() =
        assertEquals(DetectedLanguage.HINGLISH, LanguageDetector.detect("the weather is nice yaar ok"))

    @Test fun oneHitInLongSentence_staysEnglish() =
        assertEquals(
            DetectedLanguage.ENGLISH,
            LanguageDetector.detect("I think the weather is really nice today yaar okay"),
        )

    @Test fun english() {
        assertEquals(DetectedLanguage.ENGLISH, LanguageDetector.detect("what's the weather"))
        assertEquals(
            DetectedLanguage.ENGLISH,
            LanguageDetector.detect("can you tell me what the weather will be like tomorrow morning"),
        )
    }

    @Test fun blankOrNoLetters_isUnknown() {
        assertEquals(DetectedLanguage.UNKNOWN, LanguageDetector.detect(""))
        assertEquals(DetectedLanguage.UNKNOWN, LanguageDetector.detect("   \n"))
        assertEquals(DetectedLanguage.UNKNOWN, LanguageDetector.detect("123 ..."))
    }
}
