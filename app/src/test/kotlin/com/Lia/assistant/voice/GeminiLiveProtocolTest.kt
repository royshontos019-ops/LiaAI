package com.Lia.assistant.voice

import com.Lia.assistant.ToolDeclarations
import com.Lia.assistant.ToolParam
import com.Lia.assistant.ToolSpec
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiLiveProtocolTest {
    // ---------- setup ----------
    private fun setupObject(decls: JSONArray = JSONArray()): JSONObject =
        JSONObject(LiveMessages.setup("models/test-model", "Aoede", "Be kind.", decls)).getJSONObject("setup")

    @Test fun setup_hasRequiredFields() {
        val s = setupObject()
        assertEquals("models/test-model", s.getString("model"))
        val gc = s.getJSONObject("generationConfig")
        assertEquals("AUDIO", gc.getJSONArray("responseModalities").getString(0))
        assertEquals(
            "Aoede",
            gc.getJSONObject("speechConfig").getJSONObject("voiceConfig")
                .getJSONObject("prebuiltVoiceConfig").getString("voiceName"),
        )
        assertEquals(
            "Be kind.",
            s.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"),
        )
        assertEquals(0, s.getJSONObject("inputAudioTranscription").length())
        assertEquals(0, s.getJSONObject("outputAudioTranscription").length())
    }

    @Test fun setup_neverSetsActivityDetection() {
        val raw = LiveMessages.setup("m", "v", "s", JSONArray())
        assertFalse(raw.contains("realtimeInputConfig"))
        assertFalse(raw.contains("automaticActivityDetection"))
    }

    @Test fun setup_omitsToolsWhenThereAreNone() = assertFalse(setupObject().has("tools"))

    @Test fun setup_includesFunctionDeclarations() {
        val decls = ToolDeclarations.from(listOf(ToolSpec("device_action", "Do it", listOf(ToolParam("action", "which")))))
        val d = setupObject(decls).getJSONArray("tools").getJSONObject(0)
            .getJSONArray("functionDeclarations").getJSONObject(0)
        assertEquals("device_action", d.getString("name"))
        val params = d.getJSONObject("parameters")
        assertEquals("OBJECT", params.getString("type"))
        assertEquals("STRING", params.getJSONObject("properties").getJSONObject("action").getString("type"))
        assertEquals("action", params.getJSONArray("required").getString(0))
    }

    // ---------- outgoing ----------
    @Test fun audioMessage_format() {
        val pcm = ByteArray(100) { it.toByte() }
        val raw = LiveMessages.audio(pcm)
        assertFalse(raw.contains("\n"))
        val audio = JSONObject(raw).getJSONObject("realtimeInput").getJSONObject("audio")
        assertEquals("audio/pcm;rate=16000", audio.getString("mimeType"))
        assertArrayEquals(pcm, Base64.getDecoder().decode(audio.getString("data")))
    }

    @Test fun audioMessage_respectsLength() {
        val raw = LiveMessages.audio(ByteArray(100) { 7 }, 40)
        val data = JSONObject(raw).getJSONObject("realtimeInput").getJSONObject("audio").getString("data")
        assertEquals(40, Base64.getDecoder().decode(data).size)
    }

    @Test fun toolResponse_format() {
        val raw = LiveMessages.toolResponse("id1", "device_action", JSONObject().put("ok", true))
        val r = JSONObject(raw).getJSONObject("toolResponse").getJSONArray("functionResponses").getJSONObject(0)
        assertEquals("id1", r.getString("id"))
        assertEquals("device_action", r.getString("name"))
        assertTrue(r.getJSONObject("response").getBoolean("ok"))
    }

    // ---------- incoming ----------
    @Test fun parse_setupComplete() {
        val ev = LiveMessageParser.parse("""{"setupComplete":{}}""")
        assertEquals(1, ev.size)
        assertTrue(ev[0] === LiveEvent.SetupComplete)
    }

    @Test fun parse_audioChunk() {
        val ev = LiveMessageParser.parse(
            """{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AQIDBA=="}}]}}}""",
        )
        assertEquals(1, ev.size)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), (ev[0] as LiveEvent.Audio).pcm)
    }

    @Test fun parse_ignoresNonAudioInlineData() {
        val ev = LiveMessageParser.parse(
            """{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"image/png","data":"AQID"}},{"text":"hi"}]}}}""",
        )
        assertTrue(ev.isEmpty())
    }

    @Test fun parse_badBase64IsSkipped() {
        val ev = LiveMessageParser.parse(
            """{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm","data":"!!notbase64!!"}}]}}}""",
        )
        assertTrue(ev.isEmpty())
    }

    @Test fun parse_transcripts() {
        val ev = LiveMessageParser.parse(
            """{"serverContent":{"inputTranscription":{"text":"hello "},"outputTranscription":{"text":"hi there"}}}""",
        )
        assertEquals(LiveEvent.InputTranscript("hello "), ev[0])
        assertEquals(LiveEvent.OutputTranscript("hi there"), ev[1])
    }

    @Test fun parse_combinedMessageKeepsOrder() {
        val ev = LiveMessageParser.parse(
            """{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"AQID"}}]},
               "outputTranscription":{"text":"Hello"},"turnComplete":true}}""",
        )
        assertEquals(3, ev.size)
        assertTrue(ev[0] is LiveEvent.Audio)
        assertEquals(LiveEvent.OutputTranscript("Hello"), ev[1])
        assertTrue(ev[2] === LiveEvent.TurnComplete)
    }

    @Test fun parse_interruptedThenTurnComplete() {
        val ev = LiveMessageParser.parse("""{"serverContent":{"interrupted":true,"turnComplete":true}}""")
        assertTrue(ev[0] === LiveEvent.Interrupted)
        assertTrue(ev[1] === LiveEvent.TurnComplete)
    }

    @Test fun parse_toolCalls() {
        val ev = LiveMessageParser.parse(
            """{"toolCall":{"functionCalls":[
                {"id":"c1","name":"device_action","args":{"action":"home"}},
                {"id":"c2","name":"other"}]}}""",
        )
        assertEquals(2, ev.size)
        val first = ev[0] as LiveEvent.ToolCall
        assertEquals("c1", first.id)
        assertEquals("device_action", first.name)
        assertEquals("home", first.args.getString("action"))
        val second = ev[1] as LiveEvent.ToolCall
        assertEquals("c2", second.id)
        assertEquals(0, second.args.length())
    }

    @Test fun parse_ignoresHousekeepingMessages() {
        assertTrue(LiveMessageParser.parse("""{"sessionResumptionUpdate":{"newHandle":"x","resumable":true}}""").isEmpty())
        assertTrue(LiveMessageParser.parse("""{"goAway":{"timeLeft":"30s"}}""").isEmpty())
        assertTrue(LiveMessageParser.parse("""{"usageMetadata":{"totalTokenCount":5}}""").isEmpty())
    }

    @Test fun parse_malformedJsonIsIgnored() {
        assertTrue(LiveMessageParser.parse("not json").isEmpty())
        assertTrue(LiveMessageParser.parse("").isEmpty())
    }

    // ---------- url / constants / errors ----------
    @Test fun wsUrl_format() {
        assertEquals(GeminiLiveClient.WS_URL_BASE + "?key=abc123", GeminiLiveClient.wsUrl(" abc123 "))
        assertTrue(GeminiLiveClient.wsUrl("a b+c").endsWith("?key=a+b%2Bc"))
        assertTrue(GeminiLiveClient.WS_URL_BASE.startsWith("wss://generativelanguage.googleapis.com/ws/"))
        assertTrue(GeminiLiveClient.WS_URL_BASE.endsWith("BidiGenerateContent"))
    }

    @Test fun modelConstant() = assertTrue(GeminiLiveClient.MODEL.startsWith("models/"))

    @Test fun sanitize_removesKeyEverywhere() {
        val key = "SECRET-KEY-123"
        val out = LiveErrors.sanitize("failed wss://h/x?key=$key&a=1 and $key again", key)
        assertFalse(out.contains(key))
        assertTrue(out.contains("***"))
    }

    @Test fun sanitize_truncates() = assertTrue(LiveErrors.sanitize("x".repeat(500), "k", 50).length <= 51)

    @Test fun failureText_neverLeaksKey() {
        val key = "SECRET-KEY-123"
        val t = java.io.IOException("could not reach https://h/?key=$key")
        assertFalse(LiveErrors.describeFailure(t, null, key).contains(key))
        val rejected = LiveErrors.describeFailure(t, 403, key)
        assertTrue(rejected.contains("API key"))
        assertFalse(rejected.contains(key))
        assertTrue(LiveErrors.describeFailure(t, 429, key).contains("429"))
        assertTrue(LiveErrors.describeFailure(t, 500, key).contains("500"))
    }

    @Test fun closeText_includesCodeAndSanitizedReason() {
        val s = LiveErrors.describeClose(1008, "bad key=SECRET", "SECRET")
        assertTrue(s.contains("1008"))
        assertFalse(s.contains("SECRET"))
    }
}
