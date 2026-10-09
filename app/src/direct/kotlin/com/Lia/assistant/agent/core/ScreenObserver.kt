package com.Lia.assistant.agent.core

/**
 * Turns a raw capture into an observation: drops nodes nobody can see or use, keeps each node's
 * id, never keeps what is typed in a password field, and shortens very long text.
 */
class ScreenObserver(private val maxTextLength: Int = MAX_TEXT) {

    fun observe(screen: CapturedScreen): ScreenObservation = ScreenObservation(
        observationId = screen.captureId,
        packageName = screen.packageName,
        windowClass = screen.windowClass,
        elements = screen.nodes.filter { keep(it) }.map { toElement(it) },
        screenWidth = screen.screenWidth,
        screenHeight = screen.screenHeight,
        windowStamp = screen.windowStamp,
    )

    private fun keep(node: CapturedNode): Boolean =
        node.visibleToUser &&
            !node.bounds.isEmpty() &&
            (
                node.text.isNotBlank() ||
                    node.contentDescription.isNotBlank() ||
                    node.viewId.isNotBlank() ||
                    node.clickable ||
                    node.editable ||
                    node.scrollable
                )

    private fun toElement(node: CapturedNode) = UiElement(
        id = node.index,
        text = if (node.password) "" else node.text.take(maxTextLength),
        contentDescription = node.contentDescription.take(maxTextLength),
        viewId = node.viewId,
        className = node.className,
        bounds = node.bounds,
        clickable = node.clickable,
        enabled = node.enabled,
        editable = node.editable,
        scrollable = node.scrollable,
        focused = node.focused,
        password = node.password,
        depth = node.depth,
    )

    companion object {
        const val MAX_TEXT = 1000
    }
}
