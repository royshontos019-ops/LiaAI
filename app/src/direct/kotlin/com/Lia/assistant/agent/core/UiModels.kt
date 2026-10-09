package com.Lia.assistant.agent.core

data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2

    fun isEmpty(): Boolean = width <= 0 || height <= 0
    fun contains(x: Int, y: Int): Boolean = x >= left && x < right && y >= top && y < bottom

    companion object {
        val NONE = Bounds(0, 0, 0, 0)
    }
}

/** One raw node of the screen tree, exactly as the device reported it. [index] is its place in the capture. */
data class CapturedNode(
    val index: Int,
    val text: String = "",
    val contentDescription: String = "",
    val viewId: String = "",
    val className: String = "",
    val bounds: Bounds = Bounds.NONE,
    val clickable: Boolean = false,
    val enabled: Boolean = true,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val focused: Boolean = false,
    val password: Boolean = false,
    val visibleToUser: Boolean = true,
    val depth: Int = 0,
) {
    val label: String get() = text.ifBlank { contentDescription }
}

/** A snapshot of the screen. [captureId] only ever goes up, so the newest capture is the latest. */
data class CapturedScreen(
    val captureId: Long,
    val packageName: String,
    val windowClass: String,
    val nodes: List<CapturedNode>,
    val screenWidth: Int,
    val screenHeight: Int,
    val windowStamp: Long,
)

/** A node worth acting on or reading. [id] is the node's index in its capture. */
data class UiElement(
    val id: Int,
    val text: String,
    val contentDescription: String,
    val viewId: String,
    val className: String,
    val bounds: Bounds,
    val clickable: Boolean,
    val enabled: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val focused: Boolean,
    val password: Boolean,
    val depth: Int,
) {
    val label: String get() = text.ifBlank { contentDescription }
}

data class ScreenObservation(
    val observationId: Long,
    val packageName: String,
    val windowClass: String,
    val elements: List<UiElement>,
    val screenWidth: Int,
    val screenHeight: Int,
    val windowStamp: Long,
) {
    /** Changes whenever what is on screen changes. Used to tell "nothing happened" from "something happened". */
    val signature: Int get() = listOf(packageName, windowClass, elements.map { it.label }).hashCode()

    fun element(id: Int): UiElement? = elements.firstOrNull { it.id == id }

    fun containsText(text: String): Boolean {
        if (text.isBlank()) return false
        return elements.any {
            !it.password &&
                (it.text.contains(text, ignoreCase = true) || it.contentDescription.contains(text, ignoreCase = true))
        }
    }
}
