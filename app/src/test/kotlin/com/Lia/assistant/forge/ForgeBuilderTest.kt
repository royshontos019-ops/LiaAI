package com.Lia.assistant.forge

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A page of about [size] characters that ends properly. */
private fun page(size: Int) = "<!DOCTYPE html><html><body>" + "x".repeat(size) + "</body></html>"

/** A page of about [size] characters that was cut off before </html>. */
private fun cut(size: Int) = "<!DOCTYPE html><html><body><p>" + "x".repeat(size)

private class Answer(val text: String, val result: StreamResult = StreamResult.Completed("STOP"), val pieces: Int = 3)

private class FakeStreamer(vararg answers: Answer) : SiteStreamer {
    private val queue = answers.toMutableList()
    val requests = mutableListOf<ForgeRequestText>()

    override suspend fun stream(apiKey: String, request: ForgeRequestText, onText: (String) -> Unit): StreamResult {
        requests += request
        val answer = queue.removeAt(0)
        val text = answer.text
        val step = maxOf(1, text.length / answer.pieces)
        var at = 0
        while (at < text.length) {
            onText(text.substring(at, minOf(text.length, at + step)))
            at += step
        }
        return answer.result
    }
}

class ForgeBuilderTest {
    private val request = ForgeSystemPrompt.forNewSite("a bakery")
    private val waits = mutableListOf<Long>()
    private fun builder(streamer: SiteStreamer) = ForgeBuilder(streamer) { waits += it }

    private suspend fun build(streamer: SiteStreamer, onPartial: (String) -> Unit = {}) =
        builder(streamer).build("key", request, onPartial)

    @Test fun aGoodPageIsAcceptedAtOnce() = runTest {
        val html = page(3000)
        val streamer = FakeStreamer(Answer(html))
        assertEquals(BuildOutcome.Success(html), build(streamer))
        assertEquals(1, streamer.requests.size)
        assertTrue(waits.isEmpty())
    }

    @Test fun fencesAreRemovedFromTheResult() = runTest {
        val html = page(3000)
        val out = build(FakeStreamer(Answer("```html\n$html\n```")))
        assertEquals(BuildOutcome.Success(html), out)
    }

    @Test fun partialTextGrowsAndIsCleaned() = runTest {
        val seen = mutableListOf<String>()
        build(FakeStreamer(Answer("```html\n" + page(3000) + "\n```", pieces = 6))) { seen += it }
        assertTrue(seen.size >= 2)
        assertTrue(seen.none { it.contains("```") })
        assertTrue(seen.zipWithNext().all { (a, b) -> b.length >= a.length })
    }

    // ---- overloaded ----
    @Test fun overloadedWaitsFourSecondsThenTriesOnce() = runTest {
        val html = page(3000)
        val streamer = FakeStreamer(
            Answer("", StreamResult.HttpError(503, "UNAVAILABLE", "overloaded")),
            Answer(html),
        )
        assertEquals(BuildOutcome.Success(html), build(streamer))
        assertEquals(listOf(4000L), waits)
        assertEquals(2, streamer.requests.size)
        assertEquals("the retry is the same request", streamer.requests[0], streamer.requests[1])
    }

    @Test fun overloadedTwiceFailsWithAFriendlyMessage() = runTest {
        val streamer = FakeStreamer(
            Answer("", StreamResult.HttpError(503, "UNAVAILABLE", null)),
            Answer("", StreamResult.HttpError(503, "UNAVAILABLE", null)),
        )
        val out = build(streamer) as BuildOutcome.Failure
        assertTrue(out.message, out.message.contains("busy"))
        assertEquals(listOf(4000L), waits)
        assertEquals(2, streamer.requests.size)
    }

    @Test fun otherHttpErrorsDoNotRetry() = runTest {
        val streamer = FakeStreamer(Answer("", StreamResult.HttpError(403, "PERMISSION_DENIED", null)))
        val out = build(streamer) as BuildOutcome.Failure
        assertTrue(out.message.contains("refused"))
        assertEquals(1, streamer.requests.size)
        assertTrue(waits.isEmpty())
    }

    @Test fun noConnectionAndNoModelFailAtOnce() = runTest {
        assertEquals(
            BuildOutcome.Failure(ForgeErrors.NO_CONNECTION),
            build(FakeStreamer(Answer("", StreamResult.Network))),
        )
        assertEquals(
            BuildOutcome.Failure(ForgeErrors.NO_MODEL),
            build(FakeStreamer(Answer("", StreamResult.NoModel))),
        )
    }

    // ---- too short / never finished ----
    @Test fun aTooShortPageIsRetriedOnceWithAHint() = runTest {
        val html = page(3000)
        val streamer = FakeStreamer(Answer("<!DOCTYPE html><html><body>Sorry</body></html>"), Answer(html))
        assertEquals(BuildOutcome.Success(html), build(streamer))
        assertEquals(2, streamer.requests.size)
        assertFalse(streamer.requests[0].user.contains(ForgeSystemPrompt.RETRY_HINT))
        assertTrue(streamer.requests[1].user.endsWith(ForgeSystemPrompt.RETRY_HINT))
        assertTrue(streamer.requests[1].user.contains("a bakery"))
        assertEquals(streamer.requests[0].system, streamer.requests[1].system)
    }

    @Test fun aPageWithoutTheClosingTagIsRetried() = runTest {
        // finishReason RECITATION: the answer just stops.
        val html = page(3000)
        val streamer = FakeStreamer(Answer(cut(5000), StreamResult.Completed("RECITATION")), Answer(html))
        assertEquals(BuildOutcome.Success(html), build(streamer))
        assertEquals(2, streamer.requests.size)
    }

    @Test fun theHintRetryHappensOnlyOnce() = runTest {
        val streamer = FakeStreamer(Answer(cut(2000)), Answer(cut(2000)))
        val out = build(streamer)
        assertEquals(BuildOutcome.Failure(ForgeErrors.STOPPED_EARLY), out)
        assertEquals(2, streamer.requests.size)
    }

    @Test fun aLongCutOffPageIsAcceptedAndRepairedAfterTheRetry() = runTest {
        val streamer = FakeStreamer(Answer(cut(15_000)), Answer(cut(900)))
        val out = build(streamer) as BuildOutcome.Success
        assertTrue("the longer first try is kept", out.html.length > 14_000)
        assertTrue(out.html.trimEnd().endsWith("</html>"))
        assertTrue(out.html.contains("</body>"))
        assertEquals(2, streamer.requests.size)
    }

    @Test fun aLongCutOffRetryIsAcceptedToo() = runTest {
        val streamer = FakeStreamer(Answer(cut(500)), Answer(cut(16_000)))
        val out = build(streamer) as BuildOutcome.Success
        assertTrue(out.html.length > 14_000)
        assertTrue(out.html.trimEnd().endsWith("</html>"))
    }

    @Test fun aShortCutOffPageFailsWithTheStoppedMessage() = runTest {
        val streamer = FakeStreamer(Answer(cut(13_000)), Answer(cut(13_000)))
        val out = build(streamer) as BuildOutcome.Failure
        assertEquals("Gemini stopped before finishing", out.message)
    }

    @Test fun aLongPageThatIsNotHtmlIsNotAccepted() = runTest {
        val streamer = FakeStreamer(Answer("I am sorry. ".repeat(2000)), Answer("I am sorry. ".repeat(2000)))
        assertEquals(BuildOutcome.Failure(ForgeErrors.STOPPED_EARLY), build(streamer))
    }

    @Test fun anOverloadRetryDoesNotUseUpTheHintRetry() = runTest {
        val html = page(3000)
        val streamer = FakeStreamer(
            Answer("", StreamResult.HttpError(503, "UNAVAILABLE", null)),
            Answer(cut(1000)),
            Answer(html),
        )
        assertEquals(BuildOutcome.Success(html), build(streamer))
        assertEquals(3, streamer.requests.size)
        assertEquals(listOf(4000L), waits)
        assertTrue(streamer.requests[2].user.endsWith(ForgeSystemPrompt.RETRY_HINT))
    }

    @Test fun thePartialStartsAgainOnTheRetry() = runTest {
        val seen = mutableListOf<Int>()
        build(FakeStreamer(Answer(cut(4000), pieces = 2), Answer(page(3000), pieces = 2))) { seen += it.length }
        assertTrue("it shrank when the second try began", seen.zipWithNext().any { (a, b) -> b < a })
    }
}
