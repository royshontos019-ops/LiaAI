package com.Lia.assistant.forge

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun goodPage(size: Int = 3000) =
    "<!DOCTYPE html><html><head><script src=\"https://cdn.tailwindcss.com\"></script></head><body>" +
        "<header class=\"a\">logo</header><section id=\"hero\" class=\"b\">" + "x".repeat(size) + "</section><footer>bye</footer></body></html>"

private class MemoryStore : SiteStore {
    val pages = LinkedHashMap<File, String>()
    var failSave = false
    override fun save(html: String): File? {
        if (failSave) return null
        val f = File("website_${pages.size + 1}.html")
        pages[f] = html
        return f
    }
    override fun latest(): File? = pages.keys.lastOrNull()
    override fun read(file: File): String? = pages[file]
}

private class FakeEnv : ForgeEnvironment {
    var key = "key"
    var online = true
    val finished = mutableListOf<ForgeUiState>()
    override fun apiKey() = key
    override fun isOnline() = online
    override fun onFinished(state: ForgeUiState) {
        finished += state
    }
}

private class Script(val block: suspend (ForgeRequestText, (String) -> Unit) -> StreamResult) : SiteStreamer {
    val requests = mutableListOf<ForgeRequestText>()
    override suspend fun stream(apiKey: String, request: ForgeRequestText, onText: (String) -> Unit): StreamResult {
        requests += request
        return block(request, onText)
    }
}

class ForgeSessionTest {
    private val store = MemoryStore()
    private val env = FakeEnv()
    private var now = 1_000_000L

    private fun session(streamer: SiteStreamer) = ForgeSession(
        scope = CoroutineScope(Dispatchers.Unconfined),
        streamer = streamer,
        store = store,
        env = env,
        clock = { now },
        pause = {},
    )

    private fun okStreamer(page: String = goodPage()) = Script { _, onText ->
        onText(page)
        StreamResult.Completed("STOP")
    }

    // ---- the answers to start ----
    @Test fun startRefusesWithoutAKeyOfflineOrWithoutAPrompt() {
        val s = session(okStreamer())
        env.key = ""
        assertEquals(StartResult.MissingKey, s.start("a bakery"))
        env.key = "key"
        env.online = false
        assertEquals(StartResult.Offline, s.start("a bakery"))
        env.online = true
        assertEquals(StartResult.NoPrompt, s.start("   "))
        assertEquals(ForgePhase.IDLE, s.state.value.phase)
        assertFalse(s.pendingOpen.value)
        for (r in listOf(StartResult.Started, StartResult.MissingKey, StartResult.Offline, StartResult.Busy, StartResult.NothingToEdit, StartResult.NoPrompt)) {
            assertTrue(r.message.isNotBlank())
        }
    }

    // ---- a good build ----
    @Test fun aBuildEndsDoneWithASavedPage() {
        val s = session(okStreamer())
        assertEquals(StartResult.Started, s.start("  A bakery called Sweet Crumbs  "))

        val st = s.state.value
        assertEquals(ForgePhase.DONE, st.phase)
        assertEquals("A bakery called Sweet Crumbs", st.prompt)
        assertFalse(st.isEdit)
        assertEquals(1f, st.progress, 0f)
        assertEquals(ForgeStage.FINISHING, st.stage)
        assertNotNull(st.file)
        assertNull(st.error)
        assertEquals(listOf("Header", "Hero", "Footer"), st.sections)

        val saved = store.pages.getValue(st.file!!)
        assertTrue("saved with the safety net", saved.contains(ForgeHtml.SAFETY_NET_ID))
        assertEquals(saved, s.partialHtml())
        assertEquals(1, env.finished.size)
        assertEquals(ForgePhase.DONE, env.finished.single().phase)
    }

    @Test fun theLogTellsTheStory() {
        val s = session(okStreamer())
        s.start("a bakery")
        val log = s.state.value.log
        assertEquals("Planning", log.first())
        assertTrue(log.contains("Laying out Hero"))
        assertEquals("Done", log.last())
        assertTrue(s.state.value.logSeq >= log.size)
    }

    @Test fun startOpensTheScreenOnceUntilConsumed() {
        val s = session(okStreamer())
        assertFalse(s.pendingOpen.value)
        s.start("a bakery")
        assertTrue(s.pendingOpen.value)
        s.consumeOpen()
        assertFalse(s.pendingOpen.value)
    }

    // ---- throttle and log lines ----
    @Test fun stateIsUpdatedAtMostEvery120Milliseconds() {
        lateinit var s: ForgeSession
        val parts = listOf("<!DOCTYPE html><html><body>", "<p>one</p>", "<p>two two</p>", "<p>three three three</p>")
        val lengths = parts.runningFold("") { acc, p -> acc + p }.drop(1).map { it.length }
        val steps = listOf(0L, 50L, 200L, 10L)
        val seen = mutableListOf<Int>()
        val streamer = Script { _, onText ->
            for ((i, p) in parts.withIndex()) {
                now += steps[i]
                onText(p)
                seen += s.state.value.metrics.chars
            }
            // Finish with a whole page so the build is accepted and not retried.
            onText("z".repeat(2000) + "</body></html>")
            StreamResult.Completed("STOP")
        }
        s = session(streamer)
        s.start("x")
        assertEquals("first chunk shows at once", lengths[0], seen[0])
        assertEquals("50 ms later: unchanged", lengths[0], seen[1])
        assertEquals("200 ms later: updated", lengths[2], seen[2])
        assertEquals("10 ms later: unchanged", lengths[2], seen[3])
        assertEquals(ForgePhase.DONE, s.state.value.phase)
    }

    @Test fun aLogLineIsAddedForEvery10KbWritten() {
        val html = "<!DOCTYPE html><html><body>" + "y".repeat(25_000)
        val streamer = Script { _, onText ->
            onText(html)
            StreamResult.Completed("STOP")
        }
        val s = session(streamer)
        s.start("x")
        val log = s.state.value.log
        assertTrue(log.contains("10 KB written"))
        assertTrue(log.contains("20 KB written"))
        assertFalse(log.contains("30 KB written"))
    }

    @Test fun stageLabelsAreLoggedAsTheyAreReached() {
        val streamer = Script { _, onText ->
            onText("<!DOCTYPE html><html><head></head><body><section id=\"hero\"></section>")
            now += 200
            onText("<canvas></canvas>")
            now += 200
            onText("<script>gsap.to('.a',{})</script>" + "z".repeat(2000) + "</body></html>")
            StreamResult.Completed("STOP")
        }
        val s = session(streamer)
        s.start("x")
        val log = s.state.value.log
        val labels = ForgeStage.entries.map { it.label }
        val seen = log.filter { it in labels }
        assertTrue("stages only move forward: $seen", seen.zipWithNext().all { (a, b) -> labels.indexOf(a) < labels.indexOf(b) })
        assertTrue(seen.contains("Visuals"))
        assertTrue(seen.contains("Finishing") || seen.contains("Motion"))
    }

    @Test fun aRetryInsideABuildIsLogged() {
        val calls = intArrayOf(0)
        val streamer = Script { _, onText ->
            calls[0]++
            if (calls[0] == 1) {
                onText("<!DOCTYPE html><html><body>" + "a".repeat(1200)) // never finishes: the builder retries
            } else {
                now += 200
                onText("<!DOCTYPE html><html><body>short")
            }
            StreamResult.Completed("STOP")
        }
        val s = session(streamer)
        s.start("x")
        assertEquals(ForgePhase.FAILED, s.state.value.phase)
        assertEquals("Gemini stopped before finishing", s.state.value.error)
        assertTrue(s.state.value.log.contains("Trying again"))
    }

    // ---- failures ----
    @Test fun aFailureEndsFailedWithAFriendlyMessageAndCanBeRetried() {
        var good = false
        val streamer = Script { _, onText ->
            if (!good) {
                StreamResult.HttpError(403, "PERMISSION_DENIED", "raw secret text")
            } else {
                onText(goodPage())
                StreamResult.Completed("STOP")
            }
        }
        val s = session(streamer)
        s.start("a bakery")
        assertEquals(ForgePhase.FAILED, s.state.value.phase)
        assertTrue(s.state.value.error!!.contains("refused"))
        assertFalse(s.state.value.error!!.contains("secret"))
        assertEquals(ForgePhase.FAILED, env.finished.single().phase)

        good = true
        assertEquals(StartResult.Started, s.retry())
        assertEquals(ForgePhase.DONE, s.state.value.phase)
        assertEquals("a bakery", s.state.value.prompt)
        assertEquals(2, streamer.requests.size)
        assertEquals(streamer.requests[0], streamer.requests[1])
    }

    @Test fun retryWithNothingBeforeItHasNothingToDo() {
        assertEquals(StartResult.NothingToEdit, session(okStreamer()).retry())
    }

    @Test fun aSaveFailureIsReported() {
        store.failSave = true
        val s = session(okStreamer())
        s.start("a bakery")
        assertEquals(ForgePhase.FAILED, s.state.value.phase)
        assertTrue(s.state.value.error!!.contains("save"))
    }

    @Test fun anUnexpectedErrorBecomesAFailureNotACrash() {
        val s = session(Script { _, _ -> throw IllegalStateException("boom") })
        s.start("a bakery")
        assertEquals(ForgePhase.FAILED, s.state.value.phase)
        assertFalse(s.state.value.error!!.contains("boom"))
    }

    // ---- busy and cancel ----
    @Test fun whileBuildingAnotherStartOrEditIsBusy() {
        val gate = CompletableDeferred<Unit>()
        val streamer = Script { _, onText ->
            gate.await()
            onText(goodPage())
            StreamResult.Completed("STOP")
        }
        val s = session(streamer)
        assertEquals(StartResult.Started, s.start("first"))
        assertEquals(ForgePhase.BUILDING, s.state.value.phase)
        assertEquals(StartResult.Busy, s.start("second"))
        assertEquals(StartResult.Busy, s.edit("make it blue"))
        assertEquals(StartResult.Busy, s.retry())
        assertEquals(1, streamer.requests.size)

        gate.complete(Unit)
        assertEquals(ForgePhase.DONE, s.state.value.phase)
        assertEquals("first", s.state.value.prompt)
    }

    @Test fun cancelStopsTheBuildAndNothingIsSavedOrAnnounced() {
        val gate = CompletableDeferred<Unit>()
        val s = session(
            Script { _, onText ->
                gate.await()
                onText(goodPage())
                StreamResult.Completed("STOP")
            },
        )
        s.start("a bakery")
        s.cancel()
        assertEquals(ForgePhase.CANCELLED, s.state.value.phase)
        assertTrue(s.state.value.log.contains("Cancelled"))

        gate.complete(Unit)
        assertEquals("a cancelled build never finishes", ForgePhase.CANCELLED, s.state.value.phase)
        assertTrue(store.pages.isEmpty())
        assertTrue(env.finished.isEmpty())
    }

    @Test fun cancelWhenNothingIsBuildingDoesNothing() {
        val s = session(okStreamer())
        s.cancel()
        assertEquals(ForgePhase.IDLE, s.state.value.phase)
    }

    @Test fun afterACancelANewBuildWorks() {
        val gate = CompletableDeferred<Unit>()
        var first = true
        val s = session(
            Script { _, onText ->
                if (first) {
                    first = false
                    gate.await()
                }
                onText(goodPage())
                StreamResult.Completed("STOP")
            },
        )
        s.start("one")
        s.cancel()
        assertEquals(StartResult.Started, s.start("two"))
        assertEquals(ForgePhase.DONE, s.state.value.phase)
        assertEquals("two", s.state.value.prompt)
    }

    // ---- edit ----
    @Test fun editNeedsAPageFirst() {
        assertEquals(StartResult.NothingToEdit, session(okStreamer()).edit("make it blue"))
    }

    @Test fun editSendsTheCurrentPageAndTheChange() {
        val first = goodPage(2500)
        val second = goodPage(3500)
        var call = 0
        val streamer = Script { _, onText ->
            call++
            onText(if (call == 1) first else second)
            StreamResult.Completed("STOP")
        }
        val s = session(streamer)
        s.start("a bakery")
        assertEquals(StartResult.Started, s.edit("  make the header blue  "))

        val request = streamer.requests[1]
        assertTrue(request.user.contains("make the header blue"))
        assertTrue("the current page travels with the change", request.user.contains("x".repeat(2500)))
        assertTrue(request.system.contains("EDITING"))
        assertTrue(s.state.value.isEdit)
        assertEquals(ForgePhase.DONE, s.state.value.phase)
        assertEquals(2, store.pages.size)
    }

    @Test fun editWithNoChangeTextIsRefused() {
        val s = session(okStreamer())
        s.start("a bakery")
        assertEquals(StartResult.NoPrompt, s.edit("  "))
    }

    // ---- saved pages, reset ----
    @Test fun openSavedShowsAnEarlierPage() {
        val file = store.save("<html><body>old</body></html>")!!
        val s = session(okStreamer())
        s.openSaved(file)
        assertEquals(ForgePhase.DONE, s.state.value.phase)
        assertEquals(file, s.state.value.file)
        assertEquals("<html><body>old</body></html>", s.partialHtml())
        assertFalse("opening a saved page does not call for the screen", s.pendingOpen.value)
    }

    @Test fun resetGoesBackToIdle() {
        val s = session(okStreamer())
        s.start("a bakery")
        s.reset()
        assertEquals(ForgePhase.IDLE, s.state.value.phase)
        assertNull(s.state.value.file)
        assertEquals("", s.partialHtml())
    }

    @Test fun buildIdsGoUpSoOldBuildsCanBeTold() {
        val s = session(okStreamer())
        s.start("one")
        val first = s.state.value.buildId
        s.start("two")
        assertTrue(s.state.value.buildId > first)
    }
}
