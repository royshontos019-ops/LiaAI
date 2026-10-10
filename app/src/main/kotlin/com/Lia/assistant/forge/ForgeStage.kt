package com.Lia.assistant.forge

/** The six steps shown on the stage track. */
enum class ForgeStage(val label: String) {
    PLANNING("Planning"),
    STRUCTURE("Structure"),
    STYLING("Styling"),
    VISUALS("Visuals"),
    MOTION("Motion"),
    FINISHING("Finishing"),
}

/**
 * Works out how far a half-written page has got. The page streams from top to bottom, so every
 * test below only asks "has this text appeared yet?" and the answer can only go from no to yes:
 * the stage never goes backwards while a page grows.
 */
object ForgeStageDetector {
    private const val STYLED_CLASS_COUNT = 25

    /** About how long a finished page is. Only used to turn a length into a progress guess. */
    const val EXPECTED_CHARS = 36_000

    private val VISUAL_MARKERS = listOf("<canvas", "<img", "<svg", "<video", "picsum.photos", "pravatar", "three.module", "from 'three'", "from \"three\"")
    private val MOTION_MARKERS = listOf("gsap", "scrolltrigger", "requestanimationframe", "intersectionobserver", "@keyframes")

    fun detect(partialHtml: String): ForgeStage {
        val body = partialHtml.indexOf("<body", ignoreCase = true)
        // Until the body starts we only have the head (fonts, Tailwind, settings): still planning.
        if (body < 0) return ForgeStage.PLANNING

        if (partialHtml.indexOf("</body>", body, ignoreCase = true) >= 0 ||
            partialHtml.indexOf("</html>", body, ignoreCase = true) >= 0
        ) {
            return ForgeStage.FINISHING
        }
        if (MOTION_MARKERS.any { partialHtml.indexOf(it, body, ignoreCase = true) >= 0 }) return ForgeStage.MOTION
        if (VISUAL_MARKERS.any { partialHtml.indexOf(it, body, ignoreCase = true) >= 0 }) return ForgeStage.VISUALS
        if (partialHtml.indexOf("</style>", body, ignoreCase = true) >= 0 || classCount(partialHtml, body) >= STYLED_CLASS_COUNT) {
            return ForgeStage.STYLING
        }
        return ForgeStage.STRUCTURE
    }

    private fun classCount(html: String, from: Int): Int {
        var count = 0
        var at = from
        while (count < STYLED_CLASS_COUNT) {
            at = html.indexOf("class=\"", at, ignoreCase = true)
            if (at < 0) break
            count++
            at += 7
        }
        return count
    }

    private val OPEN_TAG = Regex("<(section|header|footer)\\b([^>]*)>", RegexOption.IGNORE_CASE)
    private val NAME_ATTR = Regex("(?:id|data-section|aria-label)\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val HEADING = Regex("<h[1-3][^>]*>(.*?)</h[1-3]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ANY_TAG = Regex("<[^>]*>")

    /** The names of the parts laid out so far, in page order: "Header", "Hero", "About", ..., "Footer". */
    fun sections(partialHtml: String): List<String> {
        val names = ArrayList<String>()
        var unnamed = 0
        for (m in OPEN_TAG.findAll(partialHtml)) {
            val tag = m.groupValues[1].lowercase()
            val name = when (tag) {
                "header" -> "Header"
                "footer" -> "Footer"
                else -> {
                    unnamed++
                    fromAttributes(m.groupValues[2]) ?: fromHeading(partialHtml, m.range.last + 1) ?: "Section $unnamed"
                }
            }
            names += name
        }
        return names
    }

    private fun fromAttributes(attrs: String): String? {
        val raw = NAME_ATTR.find(attrs)?.groupValues?.get(1)?.trim().orEmpty()
        val words = raw.split('-', '_', ' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        return words.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }.take(40)
    }

    private fun fromHeading(html: String, from: Int): String? {
        // Look only inside this section: stop at its end or at the next section.
        val stop = listOf("</section", "<section").map { html.indexOf(it, from, ignoreCase = true) }.filter { it >= 0 }.minOrNull()
        val window = html.substring(from, minOf(html.length, from + 700, stop ?: Int.MAX_VALUE))
        val text = HEADING.find(window)?.groupValues?.get(1)?.replace(ANY_TAG, "")?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        return text.take(40).ifEmpty { null }
    }

    /** A 0..0.97 guess from the stage and the length. Only a saved page reaches 1. */
    fun progress(stage: ForgeStage, chars: Int): Float {
        val byStage = stage.ordinal / (ForgeStage.entries.size - 1f)
        val bySize = (chars / EXPECTED_CHARS.toFloat()).coerceIn(0f, 1f)
        return (0.03f + 0.94f * (0.5f * byStage + 0.5f * bySize)).coerceIn(0f, 0.97f)
    }
}
