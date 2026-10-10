package com.Lia.assistant.agent

import com.Lia.assistant.agent.core.Bounds
import com.Lia.assistant.agent.core.CapturedNode
import com.Lia.assistant.agent.core.CapturedScreen
import com.Lia.assistant.agent.core.DriverResult
import com.Lia.assistant.agent.core.ScreenDriver
import com.Lia.assistant.agent.core.ScreenObservation
import com.Lia.assistant.agent.core.ScrollDirection
import com.Lia.assistant.agent.core.UiElement

/** One screen of the scripted world. [onClick] maps a clicked label to the screen that follows. */
class FakeScreen(
    val packageName: String,
    val nodes: List<CapturedNode>,
    val onClick: Map<String, String> = emptyMap(),
    val onBack: String? = null,
    val windowClass: String = "FakeWindow",
)

/**
 * A scripted phone for tests. It follows the real driver's rules: a capture gets a new id, and after
 * any action the old observation is stale until the next capture. Everything it was asked to do is
 * written to [log] ("click:Share", "text:Hello", "back"...), so tests can check what did NOT happen.
 */
class FakeScreenDriver(private val screens: Map<String, FakeScreen>, start: String) : ScreenDriver {
    var current: String = start

    val log = mutableListOf<String>()

    /** The next this-many clicks answer STALE, as if the screen kept changing. */
    var staleClicks = 0

    /** Clicking these labels works but changes nothing (a post that never starts). */
    val noEffectLabels = mutableSetOf<String>()

    var captureReturnsNull = false

    private val typed = HashMap<String, String>()
    private var counter = 0L
    private var latestCapture = -1L

    fun clicked(label: String): Boolean = clickCount(label) > 0
    fun clickCount(label: String): Int = log.count { it == "click:$label" }

    override suspend fun capture(): CapturedScreen? {
        if (captureReturnsNull) return null
        val screen = screens.getValue(current)
        counter++
        latestCapture = counter
        val nodes = screen.nodes.map { n ->
            val text = typed["$current:${n.index}"]
            if (text != null && n.editable) n.copy(text = text) else n
        }
        return CapturedScreen(counter, screen.packageName, screen.windowClass, nodes, 1080, 2400, current.hashCode().toLong())
    }

    override suspend fun clickElement(observation: ScreenObservation, element: UiElement): DriverResult {
        if (observation.observationId != latestCapture) return DriverResult.STALE
        if (staleClicks > 0) {
            staleClicks--
            return DriverResult.STALE
        }
        return press(element.label)
    }

    override suspend fun gestureTap(x: Int, y: Int): DriverResult {
        val hit = screens.getValue(current).nodes.firstOrNull { it.clickable && it.bounds.contains(x, y) }
            ?: return DriverResult.FAILED
        return press(hit.label)
    }

    private fun press(label: String): DriverResult {
        log += "click:$label"
        latestCapture = -1
        if (label in noEffectLabels) return DriverResult.OK
        screens.getValue(current).onClick[label]?.let { current = it }
        return DriverResult.OK
    }

    override suspend fun setText(text: String): DriverResult {
        val field = screens.getValue(current).nodes.firstOrNull { it.editable && it.focused }
            ?: return DriverResult.NOT_FOUND
        typed["$current:${field.index}"] = text
        log += "text:$text"
        return DriverResult.OK
    }

    override suspend fun scroll(direction: ScrollDirection): DriverResult {
        log += "scroll:${direction.name.lowercase()}"
        latestCapture = -1
        return DriverResult.OK
    }

    override suspend fun back(): DriverResult {
        log += "back"
        latestCapture = -1
        screens.getValue(current).onBack?.let { current = it }
        return DriverResult.OK
    }

    override suspend fun home(): DriverResult {
        log += "home"
        latestCapture = -1
        return DriverResult.OK
    }
}

/** A node for the scripted world. [top] decides where it sits, so TOP/BOTTOM regions work. */
fun node(
    index: Int,
    text: String = "",
    desc: String = "",
    id: String = "",
    cls: String = "android.widget.TextView",
    top: Int = 100 + index * 150,
    clickable: Boolean = false,
    editable: Boolean = false,
    focused: Boolean = false,
    password: Boolean = false,
) = CapturedNode(
    index = index,
    text = text,
    contentDescription = desc,
    viewId = id,
    className = cls,
    bounds = Bounds(50, top, 1030, top + 120),
    clickable = clickable,
    enabled = true,
    editable = editable,
    scrollable = false,
    focused = focused,
    password = password,
    visibleToUser = true,
    depth = 1,
)
