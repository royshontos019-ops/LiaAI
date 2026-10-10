package com.Lia.assistant.forge

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where finished pages are kept. */
interface SiteStore {
    fun save(html: String): File?
    fun latest(): File?
    fun read(file: File): String?
}

/** What the session needs from the phone. */
interface ForgeEnvironment {
    fun apiKey(): String
    fun isOnline(): Boolean

    /** Called when a build ends (done or failed). The controller posts the notification here. */
    fun onFinished(state: ForgeUiState)
}

/**
 * One build at a time, with all its state. It has no Android in it, so the whole flow (start, edit,
 * retry, cancel, retries inside a build) can be tested with a fake streamer.
 */
class ForgeSession(
    private val scope: CoroutineScope,
    streamer: SiteStreamer,
    private val store: SiteStore,
    private val env: ForgeEnvironment,
    private val clock: () -> Long = { System.currentTimeMillis() },
    pause: suspend (Long) -> Unit = { delay(it) },
) {
    companion object {
        const val EMIT_INTERVAL_MS = 120L
        const val MAX_LOG_LINES = 200
        const val KB_STEP = 10_000
    }

    private class BuildJob(val prompt: String, val currentHtml: String?)

    private val builder = ForgeBuilder(streamer, pause)

    private val _state = MutableStateFlow(ForgeUiState())
    val state: StateFlow<ForgeUiState> = _state.asStateFlow()

    private val _pendingOpen = MutableStateFlow(false)

    /** True after a build started: the screen opens the Forge when it sees this, then calls [consumeOpen]. */
    val pendingOpen: StateFlow<Boolean> = _pendingOpen.asStateFlow()

    @Volatile private var partial: String = ""
    @Volatile private var lastJob: BuildJob? = null
    private var running: Job? = null
    private var buildCounter = 0L

    // Per-build bookkeeping, touched only by the build coroutine.
    private var startedAt = 0L
    private var firstTokenAt = 0L
    private var lastEmitAt = 0L
    private var lastChars = 0
    private var loggedStage = -1
    private var loggedSections = 0
    private var loggedKb = 0
    private var logLines: List<String> = emptyList()
    private var logCounter = 0L

    fun consumeOpen() {
        _pendingOpen.value = false
    }

    /** The page as far as it has got (the finished page once done). */
    fun partialHtml(): String = partial

    @Synchronized
    fun start(prompt: String): StartResult {
        val text = prompt.trim()
        checkReady()?.let { return it }
        if (text.isEmpty()) return StartResult.NoPrompt
        launchBuild(BuildJob(text, null))
        return StartResult.Started
    }

    @Synchronized
    fun edit(change: String): StartResult {
        val text = change.trim()
        checkReady()?.let { return it }
        if (text.isEmpty()) return StartResult.NoPrompt
        val file = _state.value.file ?: store.latest()
        val html = file?.let { store.read(it) }
        if (html.isNullOrBlank()) return StartResult.NothingToEdit
        launchBuild(BuildJob(text, html))
        return StartResult.Started
    }

    @Synchronized
    fun retry(): StartResult {
        val job = lastJob ?: return StartResult.NothingToEdit
        checkReady()?.let { return it }
        launchBuild(job)
        return StartResult.Started
    }

    @Synchronized
    fun cancel() {
        if (_state.value.phase != ForgePhase.BUILDING) return
        running?.cancel()
        running = null
        addLog("Cancelled")
        _state.update { it.copy(phase = ForgePhase.CANCELLED, log = logLines, logSeq = logCounter) }
    }

    @Synchronized
    fun reset() {
        running?.cancel()
        running = null
        partial = ""
        _state.value = ForgeUiState(buildId = buildCounter)
    }

    /** Shows a page that was saved earlier (from the "my websites" list). */
    @Synchronized
    fun openSaved(file: File) {
        running?.cancel()
        running = null
        partial = store.read(file).orEmpty()
        buildCounter++
        _state.value = ForgeUiState(
            phase = ForgePhase.DONE,
            stage = ForgeStage.FINISHING,
            progress = 1f,
            buildId = buildCounter,
            file = file,
            metrics = ForgeMetrics(chars = partial.length),
        )
    }

    // ---- internals ----------------------------------------------------------------------

    private fun checkReady(): StartResult? = when {
        _state.value.phase == ForgePhase.BUILDING -> StartResult.Busy
        env.apiKey().isBlank() -> StartResult.MissingKey
        !env.isOnline() -> StartResult.Offline
        else -> null
    }

    private fun launchBuild(job: BuildJob) {
        buildCounter++
        val id = buildCounter
        lastJob = job
        partial = ""
        startedAt = clock()
        firstTokenAt = 0L
        lastEmitAt = 0L
        lastChars = 0
        loggedStage = -1
        loggedSections = 0
        loggedKb = 0
        logLines = emptyList()
        logCounter = 0L
        addLog(ForgeStage.PLANNING.label)
        loggedStage = 0
        _state.value = ForgeUiState(
            phase = ForgePhase.BUILDING,
            prompt = job.prompt,
            isEdit = job.currentHtml != null,
            stage = ForgeStage.PLANNING,
            progress = ForgeStageDetector.progress(ForgeStage.PLANNING, 0),
            log = logLines,
            logSeq = logCounter,
            buildId = id,
        )
        _pendingOpen.value = true
        running = scope.launch { run(id, job) }
    }

    private suspend fun run(id: Long, job: BuildJob) {
        try {
            val request = if (job.currentHtml != null) {
                ForgeSystemPrompt.forEdit(job.currentHtml, job.prompt)
            } else {
                ForgeSystemPrompt.forNewSite(job.prompt)
            }
            val outcome = builder.build(env.apiKey(), request) { text -> onPartial(id, text) }
            when (outcome) {
                is BuildOutcome.Success -> finish(id, outcome.html)
                is BuildOutcome.Failure -> fail(id, outcome.message)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(id, "Something went wrong while building the website.")
        }
    }

    private fun onPartial(id: Long, text: String) {
        if (id != buildCounter) return
        partial = text
        val now = clock()
        if (firstTokenAt == 0L && text.isNotEmpty()) firstTokenAt = now
        if (now - lastEmitAt < EMIT_INTERVAL_MS) return
        lastEmitAt = now

        // The retry starts a fresh page: forget what was already announced.
        if (text.length < lastChars) {
            loggedStage = 0
            loggedSections = 0
            loggedKb = 0
            addLog("Trying again")
        }
        lastChars = text.length

        val stage = ForgeStageDetector.detect(text)
        val sections = ForgeStageDetector.sections(text)
        if (stage.ordinal > loggedStage) {
            loggedStage = stage.ordinal
            addLog(stage.label)
        }
        while (loggedSections < sections.size) {
            addLog("Laying out ${sections[loggedSections]}")
            loggedSections++
        }
        val kb = text.length / KB_STEP
        while (loggedKb < kb) {
            loggedKb++
            addLog("${loggedKb * 10} KB written")
        }

        val elapsed = (now - startedAt).coerceAtLeast(0L)
        val sinceFirst = (now - firstTokenAt).coerceAtLeast(1L)
        val metrics = ForgeMetrics(
            chars = text.length,
            sections = sections.size,
            elapsedSec = (elapsed / 1000L).toInt(),
            tokensPerSec = if (firstTokenAt > 0L) (text.length / 4f) / (sinceFirst / 1000f) else 0f,
        )
        _state.update {
            if (it.buildId != id || it.phase != ForgePhase.BUILDING) it
            else it.copy(
                stage = stage,
                progress = ForgeStageDetector.progress(stage, text.length),
                metrics = metrics,
                sections = sections,
                log = logLines,
                logSeq = logCounter,
            )
        }
    }

    private fun finish(id: Long, html: String) {
        if (id != buildCounter) return
        val page = ForgeHtml.withSafetyNet(html)
        val file = store.save(page)
        if (file == null) {
            fail(id, "I couldn't save the website on this phone.")
            return
        }
        partial = page
        val sections = ForgeStageDetector.sections(page)
        addLog("Done")
        val elapsed = (clock() - startedAt).coerceAtLeast(0L)
        val done = _state.updateIfBuilding(id) {
            it.copy(
                phase = ForgePhase.DONE,
                stage = ForgeStage.FINISHING,
                progress = 1f,
                sections = sections,
                metrics = it.metrics.copy(chars = page.length, sections = sections.size, elapsedSec = (elapsed / 1000L).toInt()),
                log = logLines,
                logSeq = logCounter,
                file = file,
                error = null,
            )
        }
        if (done != null) env.onFinished(done)
    }

    private fun fail(id: Long, message: String) {
        if (id != buildCounter) return
        addLog("Stopped")
        val failed = _state.updateIfBuilding(id) {
            it.copy(phase = ForgePhase.FAILED, error = message, log = logLines, logSeq = logCounter)
        }
        if (failed != null) env.onFinished(failed)
    }

    /** Updates the state only for build [id] and only while it is still building. Returns the new state, or null. */
    private fun MutableStateFlow<ForgeUiState>.updateIfBuilding(id: Long, change: (ForgeUiState) -> ForgeUiState): ForgeUiState? {
        var result: ForgeUiState? = null
        update {
            if (it.buildId == id && it.phase == ForgePhase.BUILDING) {
                change(it).also { next -> result = next }
            } else {
                result = null
                it
            }
        }
        return result
    }

    private fun addLog(line: String) {
        logCounter++
        logLines = (logLines + line).takeLast(MAX_LOG_LINES)
    }
}
