package com.Lia.assistant.agent.core

enum class ScreenRegion { ANY, TOP, BOTTOM, LEFT, RIGHT }

/** What to look for. Text, id and class hints are alternatives: any one of them matching is enough. */
data class TargetSpec(
    /** A name for people, used in reports ("Share button"). */
    val label: String,
    val texts: List<String> = emptyList(),
    /** True: the text must equal the label. False: containing it is enough. */
    val exact: Boolean = false,
    val viewIdParts: List<String> = emptyList(),
    val classNameParts: List<String> = emptyList(),
    val mustBeClickable: Boolean = false,
    val mustBeEditable: Boolean = false,
    val region: ScreenRegion = ScreenRegion.ANY,
    /** True: two equally good matches are an error, never a coin flip. */
    val requireUnique: Boolean = false,
)

sealed interface Resolution {
    data class Found(val element: UiElement, val score: Int) : Resolution
    data class Ambiguous(val candidates: List<UiElement>) : Resolution
    object NotFound : Resolution
}

/** Picks the best element for a [TargetSpec]. Pure Kotlin. */
class TargetResolver {

    fun resolve(observation: ScreenObservation, spec: TargetSpec): Resolution {
        val scored = observation.elements.mapNotNull { element ->
            score(element, spec, observation)?.let { Scored(element, it) }
        }
        if (scored.isEmpty()) return Resolution.NotFound

        val sorted = scored.sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.element.id })
        val best = sorted.first()
        val tied = sorted.filter { it.score == best.score }
        if (spec.requireUnique && tied.size > 1) {
            return Resolution.Ambiguous(tied.map { it.element })
        }
        return Resolution.Found(best.element, best.score)
    }

    private class Scored(val element: UiElement, val score: Int)

    private fun score(element: UiElement, spec: TargetSpec, screen: ScreenObservation): Int? {
        if (spec.mustBeClickable && !element.clickable) return null
        if (spec.mustBeEditable && !element.editable) return null
        if (!inRegion(element, spec.region, screen)) return null

        val hasCriteria = spec.texts.isNotEmpty() || spec.viewIdParts.isNotEmpty() || spec.classNameParts.isNotEmpty()
        var total = 0
        var matched = false

        val textScore = textScore(element, spec)
        if (textScore > 0) {
            total += textScore
            matched = true
        }
        if (spec.viewIdParts.any { element.viewId.contains(it, ignoreCase = true) }) {
            total += 80
            matched = true
        }
        if (spec.classNameParts.any { element.className.contains(it, ignoreCase = true) }) {
            total += 20
            matched = true
        }

        if (hasCriteria && !matched) return null
        if (!hasCriteria) total = 10
        if (element.clickable) total += 5
        if (element.enabled) total += 2
        return total
    }

    private fun textScore(element: UiElement, spec: TargetSpec): Int {
        var best = 0
        for (wanted in spec.texts) {
            if (wanted.isBlank()) continue
            if (element.text.equals(wanted, ignoreCase = true)) best = maxOf(best, 100)
            else if (!spec.exact && element.text.contains(wanted, ignoreCase = true)) best = maxOf(best, 60)
            if (element.contentDescription.equals(wanted, ignoreCase = true)) best = maxOf(best, 90)
            else if (!spec.exact && element.contentDescription.contains(wanted, ignoreCase = true)) best = maxOf(best, 50)
        }
        return best
    }

    private fun inRegion(element: UiElement, region: ScreenRegion, screen: ScreenObservation): Boolean {
        val width = screen.screenWidth
        val height = screen.screenHeight
        if (region == ScreenRegion.ANY || width <= 0 || height <= 0) return true
        return when (region) {
            ScreenRegion.TOP -> element.bounds.centerY < height / 3
            ScreenRegion.BOTTOM -> element.bounds.centerY > height * 2 / 3
            ScreenRegion.LEFT -> element.bounds.centerX < width / 3
            ScreenRegion.RIGHT -> element.bounds.centerX > width * 2 / 3
            ScreenRegion.ANY -> true
        }
    }
}
