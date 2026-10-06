package com.Lia.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceMapperTest {
    @Test fun knownVoiceKeepsCanonicalSpelling() {
        assertEquals("Aoede", VoiceMapper.toGemini("aoede"))
        assertEquals("Puck", VoiceMapper.toGemini("  PUCK "))
    }

    @Test fun unknownBlankOrNullFallsBackToAoede() {
        assertEquals("Aoede", VoiceMapper.toGemini("Nova"))
        assertEquals("Aoede", VoiceMapper.toGemini(""))
        assertEquals("Aoede", VoiceMapper.toGemini("   "))
        assertEquals("Aoede", VoiceMapper.toGemini(null))
    }
}
