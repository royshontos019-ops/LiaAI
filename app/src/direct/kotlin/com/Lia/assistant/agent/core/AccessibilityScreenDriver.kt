package com.Lia.assistant.agent.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.Lia.assistant.action.NovaAccessibilityService
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The real [ScreenDriver]: reads the screen and acts through the Accessibility service.
 *
 * Each capture remembers the live nodes, so [clickElement] can act on the exact node that was
 * observed. After any action the last capture is marked stale; the next [capture] makes a new one.
 * Tapping by position needs the service to allow gestures (canPerformGestures); without it
 * [gestureTap] reports UNSUPPORTED instead of pretending.
 */
class AccessibilityScreenDriver(
    private val serviceProvider: () -> NovaAccessibilityService? = { NovaAccessibilityService.instance },
) : ScreenDriver {

    private val captureCounter = AtomicLong(0)

    @Volatile
    private var latestCaptureId: Long = INVALID

    @Volatile
    private var latestNodes: List<AccessibilityNodeInfo> = emptyList()

    override suspend fun capture(): CapturedScreen? {
        val service = serviceProvider() ?: return null
        val root = service.rootInActiveWindow ?: return null

        val nodes = ArrayList<AccessibilityNodeInfo>()
        val captured = ArrayList<CapturedNode>()
        collect(root, 0, nodes, captured)

        val id = captureCounter.incrementAndGet()
        latestNodes = nodes
        latestCaptureId = id

        val metrics = service.resources.displayMetrics
        val packageName = root.packageName?.toString().orEmpty()
        val windowClass = root.className?.toString().orEmpty()
        val stamp = (root.windowId.toLong() shl 32) xor (packageName + windowClass).hashCode().toLong()
        return CapturedScreen(id, packageName, windowClass, captured, metrics.widthPixels, metrics.heightPixels, stamp)
    }

    private fun collect(
        node: AccessibilityNodeInfo,
        depth: Int,
        nodes: MutableList<AccessibilityNodeInfo>,
        out: MutableList<CapturedNode>,
    ) {
        if (nodes.size >= MAX_NODES || depth > MAX_DEPTH) return
        val index = nodes.size
        nodes += node

        val rect = Rect()
        node.getBoundsInScreen(rect)
        out += CapturedNode(
            index = index,
            text = node.text?.toString().orEmpty(),
            contentDescription = node.contentDescription?.toString().orEmpty(),
            viewId = node.viewIdResourceName.orEmpty(),
            className = node.className?.toString().orEmpty(),
            bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
            clickable = node.isClickable,
            enabled = node.isEnabled,
            editable = node.isEditable,
            scrollable = node.isScrollable,
            focused = node.isFocused,
            password = node.isPassword,
            visibleToUser = node.isVisibleToUser,
            depth = depth,
        )
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, depth + 1, nodes, out)
        }
    }

    override suspend fun clickElement(observation: ScreenObservation, element: UiElement): DriverResult {
        if (observation.observationId != latestCaptureId) return DriverResult.STALE
        val node = latestNodes.getOrNull(element.id) ?: return DriverResult.NOT_FOUND
        if (!node.refresh()) return DriverResult.NOT_FOUND

        // The tappable thing is often a parent of the text that was seen.
        val clickTarget = generateSequence(node) { it.parent }.take(5).firstOrNull { it.isClickable }
        val done = when {
            clickTarget != null -> clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node.isEditable ->
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ||
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            else -> return DriverResult.FAILED
        }
        if (!done) return DriverResult.FAILED
        invalidate()
        return DriverResult.OK
    }

    override suspend fun gestureTap(x: Int, y: Int): DriverResult {
        val service = serviceProvider() ?: return DriverResult.FAILED
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_MS))
            .build()

        var accepted = true
        val completed = suspendCancellableCoroutine<Boolean> { continuation ->
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }
            accepted = service.dispatchGesture(gesture, callback, null)
            if (!accepted && continuation.isActive) continuation.resume(false)
        }
        if (!accepted) return DriverResult.UNSUPPORTED
        if (!completed) return DriverResult.FAILED
        invalidate()
        return DriverResult.OK
    }

    override suspend fun setText(text: String): DriverResult {
        val service = serviceProvider() ?: return DriverResult.FAILED
        val focus = service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return DriverResult.NOT_FOUND
        if (!focus.isEditable) return DriverResult.NOT_FOUND
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (!focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) return DriverResult.FAILED
        invalidate()
        return DriverResult.OK
    }

    override suspend fun scroll(direction: ScrollDirection): DriverResult {
        val node = latestNodes.firstOrNull { it.isScrollable } ?: return DriverResult.NOT_FOUND
        if (!node.refresh()) return DriverResult.NOT_FOUND
        val action = when (direction) {
            ScrollDirection.DOWN, ScrollDirection.RIGHT -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            ScrollDirection.UP, ScrollDirection.LEFT -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        if (!node.performAction(action)) return DriverResult.FAILED
        invalidate()
        return DriverResult.OK
    }

    override suspend fun back(): DriverResult = global(AccessibilityService.GLOBAL_ACTION_BACK)

    override suspend fun home(): DriverResult = global(AccessibilityService.GLOBAL_ACTION_HOME)

    private fun global(action: Int): DriverResult {
        val service = serviceProvider() ?: return DriverResult.FAILED
        if (!service.performGlobalAction(action)) return DriverResult.FAILED
        invalidate()
        return DriverResult.OK
    }

    private fun invalidate() {
        latestCaptureId = INVALID
    }

    private companion object {
        const val INVALID = -1L
        const val MAX_NODES = 600
        const val MAX_DEPTH = 40
        const val TAP_MS = 60L
    }
}
