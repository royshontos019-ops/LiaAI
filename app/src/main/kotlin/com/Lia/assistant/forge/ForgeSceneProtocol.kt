package com.Lia.assistant.forge

import com.Lia.assistant.ui.theme.NovaColorScheme
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/** What the 3D scene needs to know, as small JSON differences between two states. */
object ForgeSceneProtocol {
    private const val PROGRESS_STEP = 0.002f

    /**
     * Only what changed since [prev]. The first call (or a new build) sends everything and
     * "reset": true. Returns null when the scene has nothing new to hear.
     */
    fun diff(prev: ForgeUiState?, next: ForgeUiState): JSONObject? {
        val fresh = prev == null || prev.buildId != next.buildId
        val old = if (fresh) null else prev
        val out = JSONObject()
        if (fresh) out.put("reset", true)
        if (old == null || old.phase != next.phase) out.put("phase", next.phase.name.lowercase())
        if (old == null || old.stage != next.stage) out.put("stage", next.stage.ordinal)
        if (old == null || abs(old.progress - next.progress) >= PROGRESS_STEP) {
            out.put("progress", (next.progress * 1000f).roundToInt() / 1000.0)
        }
        if (old == null || old.metrics.chars != next.metrics.chars) out.put("chars", next.metrics.chars)

        val knownSections = old?.sections?.size ?: 0
        if (next.sections.size > knownSections) out.put("newSections", JSONArray(next.sections.drop(knownSections)))

        val newLines = (next.logSeq - (old?.logSeq ?: 0L)).toInt().coerceIn(0, next.log.size)
        if (newLines > 0) out.put("log", JSONArray(next.log.takeLast(newLines)))

        return if (out.length() == 0) null else out
    }

    /** JSON is almost JavaScript; these two line separators are the exception. */
    fun toJs(json: JSONObject): String = json.toString().replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
}

/** The colours and options for Forge.init. The scene has no palette of its own. */
object ForgeThemeJson {
    private fun channel(v: Float): Int = (v * 255f + 0.5f).toInt().coerceIn(0, 255)

    fun hex(color: androidx.compose.ui.graphics.Color): String =
        "#%02X%02X%02X".format(channel(color.red), channel(color.green), channel(color.blue))

    fun theme(c: NovaColorScheme): JSONObject = JSONObject()
        .put("background", hex(c.background))
        .put("backgroundTop", hex(c.backgroundGradientTop))
        .put("accent", hex(c.accent))
        .put("accentSecondary", hex(c.accentSecondary))
        .put("lagoon", hex(c.lagoon))
        .put("lotus", hex(c.lotus))
        .put("textPrimary", hex(c.textPrimary))
        .put("textSecondary", hex(c.textSecondary))
        .put("textTertiary", hex(c.textTertiary))
        .put("error", hex(c.error))
        .put("success", hex(c.success))
        .put("isDark", c.isDark)

    fun options(lowRam: Boolean, reducedMotion: Boolean): JSONObject = JSONObject()
        .put("lowRam", lowRam)
        .put("reducedMotion", reducedMotion)
        .put("stageLabels", JSONArray(ForgeStage.entries.map { it.label }))
}

/** Small helpers for saved pages. */
object ForgeFiles {
    private val TITLE = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /** The page's <title>, tidied, or null. */
    fun titleOf(html: String): String? =
        TITLE.find(html)?.groupValues?.get(1)?.replace(Regex("\\s+"), " ")?.trim()?.take(60)?.ifEmpty { null }
}
