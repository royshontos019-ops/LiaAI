package com.Lia.assistant.forge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseParserTest {
    @Test fun dataLinesGiveTheirJson() =
        assertEquals("""{"a":1}""", SseParser.dataPayload("""data: {"a":1}"""))

    @Test fun noSpaceAfterTheColonIsFine() =
        assertEquals("""{"a":1}""", SseParser.dataPayload("""data:{"a":1}"""))

    @Test fun doneBlankCommentsAndOtherFieldsGiveNothing() {
        assertNull(SseParser.dataPayload("data: [DONE]"))
        assertNull(SseParser.dataPayload("data:[DONE]"))
        assertNull(SseParser.dataPayload("data:"))
        assertNull(SseParser.dataPayload(""))
        assertNull(SseParser.dataPayload(": keep-alive"))
        assertNull(SseParser.dataPayload("event: message"))
    }

    @Test fun textOfOnePart() =
        assertEquals("Hello", SseParser.extractText("""{"candidates":[{"content":{"parts":[{"text":"Hello"}]}}]}"""))

    @Test fun textOfSeveralPartsIsJoinedInOrder() =
        assertEquals(
            "<html>A</html>",
            SseParser.extractText("""{"candidates":[{"content":{"parts":[{"text":"<html>"},{"text":"A"},{"text":"</html>"}]}}]}"""),
        )

    @Test fun thoughtPartsAreSkipped() =
        assertEquals(
            "Page",
            SseParser.extractText("""{"candidates":[{"content":{"parts":[{"text":"thinking...","thought":true},{"text":"Page"}]}}]}"""),
        )

    @Test fun missingOrBrokenJsonGivesEmptyText() {
        assertEquals("", SseParser.extractText("""{"usageMetadata":{}}"""))
        assertEquals("", SseParser.extractText("""{"candidates":[]}"""))
        assertEquals("", SseParser.extractText("not json"))
        assertEquals("", SseParser.extractText(""))
    }

    @Test fun anErrorObjectIsRead() {
        val e = SseParser.extractError("""{"error":{"code":503,"message":"The model is overloaded.","status":"UNAVAILABLE"}}""")
        assertEquals(ApiError(503, "UNAVAILABLE", "The model is overloaded."), e)
    }

    @Test fun noErrorMeansNull() {
        assertNull(SseParser.extractError("""{"candidates":[]}"""))
        assertNull(SseParser.extractError("not json"))
    }

    @Test fun finishReasonIsReadWhenPresent() {
        assertEquals("STOP", SseParser.extractFinishReason("""{"candidates":[{"content":{"parts":[{"text":"x"}]},"finishReason":"STOP"}]}"""))
        assertEquals("RECITATION", SseParser.extractFinishReason("""{"candidates":[{"finishReason":"RECITATION"}]}"""))
        assertNull(SseParser.extractFinishReason("""{"candidates":[{"content":{"parts":[{"text":"x"}]}}]}"""))
        assertNull(SseParser.extractFinishReason("not json"))
    }

    @Test fun oneChunkCanCarryTextAndAFinishReason() {
        val chunk = """{"candidates":[{"content":{"parts":[{"text":"end"}]},"finishReason":"STOP"}]}"""
        assertEquals("end", SseParser.extractText(chunk))
        assertEquals("STOP", SseParser.extractFinishReason(chunk))
    }
}
