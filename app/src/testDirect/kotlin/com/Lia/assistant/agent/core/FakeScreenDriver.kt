package com.Lia.assistant.agent.core

/** A node for a fake screen. Its place in the screen and its position are filled in by [FakeScreen]. */
fun fakeNode(
    text: String = "",
    desc: String = "",
    viewId: String = "",
    clickable: Boolean = false,
    enabled: Boolean = true,
    editable: Boolean = false,
    password: Boolean = false,
    visible: Boolean = true,
    scrollable: Boolean = false,
    className: String = "android.view.View",
): CapturedNode = CapturedNode(
    index = -1,
    text = text,
    contentDescription = desc,
    viewId = viewId,
    className = className,
    clickable = clickable,
    enabled = enabled,
    editable = editable,
    scrollable = scrollable,
    password = password,
    visibleToUser = visible,
)

/**
 * One screen of the scripted test world. Nodes are stacked top to bottom, each 100 px tall, so no
 * two nodes overlap. [transitions] says where tapping a node (by its label) leads.
 */
class FakeScreen(
    val name: String,
    val packageName: String,
    nodes: List<CapturedNode>,
    val transitions: Map<String, String> = emptyMap(),
    val backTo: String? = null,
    val windowClass: String = "android.widget.FrameLayout",
) {
    val nodes: List<CapturedNode> = nodes.mapIndexed { i, node ->
        node.copy(index = i, bounds = Bounds(0, i * 120, 1080, i * 120 + 100))
    }
}

/**
 * The scripted stand-in for the phone. Behaviour switches let a test make clicks fail, make them
 * "succeed" without any effect, force a stale observation, or refuse typing. Every call is written
 * to [log], so a test can also prove what was NEVER done.
 */
class FakeScreenDriver(
    private val screens: Map<String, FakeScreen>,
    start: String,
) : ScreenDriver {

    var current: String = start
    val log = mutableListOf<String>()

    var accessibilityClick = DriverResult.OK
    var gestureResult = DriverResult.OK
    var setTextResult = DriverResult.OK
    var accessibilityClickHasEffect = true
    var gestureHasEffect = true
    var applyTextOnSet = true
    var staleOnNextClick = false
    var noScreen = false

    private var captureCounter = 0L
    private var latestCaptureId = -1L
    private var focusedIndex: Int? = null
    private var lastNodes: List<CapturedNode> = emptyList()
    private val typed = mutableMapOf<Pair<String, Int>, String>()

    val captureCount: Int get() = log.count { it == "capture" }
    val clickLog: List<String> get() = log.filter { it.startsWith("click:") }
    val gestureLog: List<String> get() = log.filter { it.startsWith("gesture:") }

    override suspend fun capture(): CapturedScreen? {
        log += "capture"
        if (noScreen) return null
        val screen = screens.getValue(current)
        val nodes = screen.nodes.map { node ->
            val override = typed[current to node.index]
            node.copy(
                text = override ?: node.text,
                focused = node.index == focusedIndex,
            )
        }
        captureCounter++
        latestCaptureId = captureCounter
        lastNodes = nodes
        return CapturedScreen(captureCounter, screen.packageName, screen.windowClass, nodes, 1080, 2400, current.hashCode().toLong())
    }

    override suspend fun clickElement(observation: ScreenObservation, element: UiElement): DriverResult {
        log += "click:${element.label}"
        if (staleOnNextClick) {
            staleOnNextClick = false
            latestCaptureId = -1L
        }
        if (observation.observationId != latestCaptureId) return DriverResult.STALE
        val node = lastNodes.getOrNull(element.id) ?: return DriverResult.NOT_FOUND
        if (accessibilityClick != DriverResult.OK) return accessibilityClick
        latestCaptureId = -1L
        if (accessibilityClickHasEffect) applyTap(node)
        return DriverResult.OK
    }

    override suspend fun gestureTap(x: Int, y: Int): DriverResult {
        log += "gesture:$x,$y"
        if (gestureResult != DriverResult.OK) return gestureResult
        latestCaptureId = -1L
        if (gestureHasEffect) {
            val node = screens.getValue(current).nodes
                .firstOrNull { it.bounds.contains(x, y) && (it.clickable || it.editable) }
            if (node != null) applyTap(node)
        }
        return DriverResult.OK
    }

    override suspend fun setText(text: String): DriverResult {
        log += "setText:$text"
        if (setTextResult != DriverResult.OK) return setTextResult
        val index = focusedIndex ?: return DriverResult.NOT_FOUND
        if (applyTextOnSet) typed[current to index] = text
        latestCaptureId = -1L
        return DriverResult.OK
    }

    override suspend fun scroll(direction: ScrollDirection): DriverResult {
        log += "scroll:$direction"
        latestCaptureId = -1L
        return DriverResult.OK
    }

    override suspend fun back(): DriverResult {
        log += "back"
        latestCaptureId = -1L
        screens.getValue(current).backTo?.let { current = it }
        focusedIndex = null
        return DriverResult.OK
    }

    override suspend fun home(): DriverResult {
        log += "home"
        latestCaptureId = -1L
        return DriverResult.OK
    }

    private fun applyTap(node: CapturedNode) {
        if (node.editable) focusedIndex = node.index
        val next = screens.getValue(current).transitions[node.label]
        if (next != null) {
            current = next
            focusedIndex = null
        }
    }
}
