package com.Lia.assistant.voice

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiTextProtocolTest {
    private fun model(name: String, vararg methods: String) = ModelInfo(name, methods.toList())

    // ---------- model list ----------
    @Test fun parseModels_readsNamesAndMethods() {
        val json = """{"models":[
            {"name":"models/a","supportedGenerationMethods":["generateContent","countTokens"]},
            {"name":"models/b"},{"displayName":"no name"}]}"""
        val models = GeminiTextProtocol.parseModels(json)
        assertEquals(2, models.size)
        assertEquals(listOf("generateContent", "countTokens"), models[0].methods)
        assertTrue(models[1].methods.isEmpty())
    }

    @Test fun parseModels_badJsonIsEmpty() {
        assertTrue(GeminiTextProtocol.parseModels("nope").isEmpty())
        assertTrue(GeminiTextProtocol.parseModels("{}").isEmpty())
    }

    @Test fun pickModel_firstFlashThatIsNotLiveImageOrTts() {
        val models = listOf(
            model("models/gemini-pro", "generateContent"),
            model("models/gemini-2.5-flash-live-preview", "generateContent"),
            model("models/gemini-2.5-flash-image", "generateContent"),
            model("models/gemini-2.5-flash-preview-tts", "generateContent"),
            model("models/gemini-2.5-flash-native-audio", "bidiGenerateContent"),
            model("models/gemini-2.5-flash", "generateContent"),
            model("models/gemini-2.5-flash-lite", "generateContent"),
        )
        assertEquals("models/gemini-2.5-flash", GeminiTextProtocol.pickModel(models))
    }

    @Test fun pickModel_fallsBackToFirstGenerateContentModel() {
        val models = listOf(
            model("models/embedding", "embedContent"),
            model("models/gemini-pro", "generateContent"),
            model("models/gemma", "generateContent"),
        )
        assertEquals("models/gemini-pro", GeminiTextProtocol.pickModel(models))
    }

    @Test fun pickModel_nullWhenNothingUsable() {
        assertNull(GeminiTextProtocol.pickModel(emptyList()))
        assertNull(GeminiTextProtocol.pickModel(listOf(model("models/e", "embedContent"))))
    }

    @Test fun modelPath_addsThePrefixOnce() {
        assertEquals("models/x", GeminiTextProtocol.modelPath("x"))
        assertEquals("models/x", GeminiTextProtocol.modelPath("models/x"))
    }

    // ---------- request ----------
    @Test fun buildRequest_rolesAndSystemInstruction() {
        val json = JSONObject(
            GeminiTextProtocol.buildRequest(
                "Be kind.",
                listOf(ChatTurn(true, "hi"), ChatTurn(false, "hello"), ChatTurn(true, "bye")),
            ),
        )
        assertEquals("Be kind.", json.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
        val contents = json.getJSONArray("contents")
        assertEquals(3, contents.length())
        assertEquals("user", contents.getJSONObject(0).getString("role"))
        assertEquals("model", contents.getJSONObject(1).getString("role"))
        assertEquals("bye", contents.getJSONObject(2).getJSONArray("parts").getJSONObject(0).getString("text"))
    }

    @Test fun buildRequest_mergesConsecutiveTurnsOfTheSameSide() {
        val contents = JSONObject(
            GeminiTextProtocol.buildRequest(
                "",
                listOf(ChatTurn(true, "hi"), ChatTurn(true, "there"), ChatTurn(false, "hello")),
            ),
        ).getJSONArray("contents")
        assertEquals(2, contents.length())
        assertEquals("hi\nthere", contents.getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text"))
    }

    @Test fun buildRequest_blankSystemInstructionIsOmitted() =
        assertFalse(JSONObject(GeminiTextProtocol.buildRequest("  ", listOf(ChatTurn(true, "x")))).has("systemInstruction"))

    @Test fun prepareHistory_dropsBlanksLimitsAndStartsWithTheUser() {
        val long = (1..60).map { ChatTurn(it % 2 == 1, "m$it") }
        val prepared = GeminiTextProtocol.prepareHistory(long)
        assertTrue(prepared.size <= GeminiTextProtocol.MAX_HISTORY_TURNS)
        assertTrue(prepared.first().isUser)
        assertEquals("m60", prepared.last().text)

        val withBlank = GeminiTextProtocol.prepareHistory(listOf(ChatTurn(false, "stray"), ChatTurn(true, " "), ChatTurn(true, "real")))
        assertEquals(listOf("real"), withBlank.map { it.text })
        assertTrue(GeminiTextProtocol.prepareHistory(emptyList()).isEmpty())
    }

    // ---------- response ----------
    @Test fun parseReply_text() {
        val r = GeminiTextProtocol.parseReply("""{"candidates":[{"content":{"parts":[{"text":"  Hello there  "}]},"finishReason":"STOP"}]}""")
        assertEquals(ParsedReply.Text("Hello there"), r)
    }

    @Test fun parseReply_joinsPartsAndSkipsThoughts() {
        val r = GeminiTextProtocol.parseReply(
            """{"candidates":[{"content":{"parts":[{"text":"hidden","thought":true},{"text":"Hello "},{"text":"world"}]}}]}""",
        )
        assertEquals(ParsedReply.Text("Hello world"), r)
    }

    @Test fun parseReply_promptBlock() =
        assertEquals(
            ParsedReply.Blocked("SAFETY"),
            GeminiTextProtocol.parseReply("""{"promptFeedback":{"blockReason":"SAFETY"}}"""),
        )

    @Test fun parseReply_finishReasonWithoutText() =
        assertEquals(
            ParsedReply.Blocked("SAFETY"),
            GeminiTextProtocol.parseReply("""{"candidates":[{"finishReason":"SAFETY"}]}"""),
        )

    @Test fun parseReply_emptyCases() {
        assertEquals(ParsedReply.Empty, GeminiTextProtocol.parseReply("""{"candidates":[]}"""))
        assertEquals(ParsedReply.Empty, GeminiTextProtocol.parseReply("""{"candidates":[{"finishReason":"STOP"}]}"""))
        assertEquals(ParsedReply.Empty, GeminiTextProtocol.parseReply("not json"))
    }

    // ---------- errors ----------
    @Test fun errorReason_readsGeminisOwnMessage() {
        assertEquals(
            "API key not valid.",
            GeminiTextProtocol.errorReason("""{"error":{"code":400,"message":"API key not valid.","status":"INVALID_ARGUMENT"}}"""),
        )
        assertNull(GeminiTextProtocol.errorReason("<html>"))
        assertNull(GeminiTextProtocol.errorReason("""{"error":{}}"""))
    }

    @Test fun httpMessage_byStatus() {
        assertTrue(GeminiTextProtocol.httpMessage(403, "API key not valid.").contains("API key"))
        assertTrue(GeminiTextProtocol.httpMessage(403, "API key not valid.").contains("403"))
        assertTrue(GeminiTextProtocol.httpMessage(429, null).contains("429"))
        assertTrue(GeminiTextProtocol.httpMessage(503, null).contains("try again", ignoreCase = true))
        assertTrue(GeminiTextProtocol.httpMessage(418, "teapot").contains("418"))
    }

    @Test fun httpMessage_neverContainsTheKey() {
        val key = "SECRET-KEY-123"
        val msg = GeminiTextProtocol.httpMessage(400, "bad request key=$key and $key", key)
        assertFalse(msg.contains(key))
    }

    // ---------- candidate list (walk past models that answer 404) ----------
    @Test fun candidateModels_flashFirstThenTheRestAndNeverLiveImageTts() {
        val models = listOf(
            model("models/gemini-pro", "generateContent"),
            model("models/gemini-2.0-flash-old", "generateContent"),
            model("models/gemini-flash-live", "generateContent"),
            model("models/gemini-2.5-flash-image", "generateContent"),
            model("models/gemini-2.5-flash-tts", "generateContent"),
            model("models/gemini-2.5-flash", "generateContent"),
            model("models/embedding", "embedContent"),
            model("models/gemma", "generateContent"),
        )
        assertEquals(
            listOf("models/gemini-2.0-flash-old", "models/gemini-2.5-flash", "models/gemini-pro", "models/gemma"),
            GeminiTextProtocol.candidateModels(models),
        )
    }

    @Test fun candidateModels_whenOnlyLiveModelsExistTheyAreTheLastResort() {
        val models = listOf(model("models/x-live", "generateContent"))
        assertEquals(listOf("models/x-live"), GeminiTextProtocol.candidateModels(models))
    }

    @Test fun candidateModels_emptyWhenNothingSupportsGenerateContent() =
        assertTrue(GeminiTextProtocol.candidateModels(listOf(model("models/e", "embedContent"))).isEmpty())

    @Test fun tryOrder_putsTheWorkingModelFirst() {
        val names = listOf("a", "b", "c")
        assertEquals(listOf("c", "a", "b"), GeminiTextProtocol.tryOrder(names, "c"))
        assertEquals(names, GeminiTextProtocol.tryOrder(names, null))
        assertEquals(names, GeminiTextProtocol.tryOrder(names, "unknown"))
    }

    @Test fun noModelMessage_listsModelsAndHidesTheKey() {
        val key = "SECRET-KEY-123"
        val msg = GeminiTextProtocol.noModelMessage(
            listOf("models/a", "models/b"), "model no longer available key=$key", key,
        )
        assertTrue(msg.contains("models/a, models/b"))
        assertTrue(msg.contains("404"))
        assertFalse(msg.contains(key))
    }
}
