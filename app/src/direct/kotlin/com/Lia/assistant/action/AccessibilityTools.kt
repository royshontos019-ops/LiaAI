package com.Lia.assistant.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject

/** Screen tools backed by [NovaAccessibilityService]. Every function returns a JSON result. */
object AccessibilityTools {
    private const val MAX_PARENT_HOPS = 8

    private fun notEnabled() = ToolJson.result("accessibility_not_enabled")

    fun readScreen(maxElements: Int = 40, maxLabelLength: Int = 40): JSONObject {
        val svc = NovaAccessibilityService.instance ?: return notEnabled()
        val root = svc.rootInActiveWindow ?: return ToolJson.result("screen_unavailable")
        try {
            val lines = ScreenFormatter.format(RealNode(root), maxElements, maxLabelLength)
            return ToolJson.result("ok")
                .put("app", root.packageName?.toString().orEmpty())
                .put("elements", lines.size)
                .put("screen", lines.joinToString("\n"))
        } finally {
            recycleQuietly(root)
        }
    }

    /**
     * Taps the best match for [text]: the node itself or its nearest clickable ancestor, else (if
     * [allowGestureFallback]) a real touch at the node's centre. Result: "tapped" / "element_not_found".
     */
    suspend fun tapText(text: String, allowGestureFallback: Boolean = true): JSONObject {
        val svc = NovaAccessibilityService.instance ?: return notEnabled()
        val query = text.trim()
        val root = svc.rootInActiveWindow ?: return ToolJson.result("element_not_found")
        try {
            val matches = root.findAccessibilityNodeInfosByText(query).orEmpty()
            val target = pickBest(matches, query) ?: return ToolJson.result("element_not_found")
            if (clickNodeOrAncestor(target)) return ToolJson.result("tapped")
            if (allowGestureFallback && tapCenter(svc, target)) return ToolJson.result("tapped")
            return ToolJson.result("element_not_found")
        } finally {
            recycleQuietly(root)
        }
    }

    /** Types into the focused input field (appends to what is already there). */
    fun typeText(text: String): JSONObject {
        val svc = NovaAccessibilityService.instance ?: return notEnabled()
        val root = svc.rootInActiveWindow ?: return ToolJson.result("no_focused_field")
        try {
            val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (field == null || !field.isEditable) return ToolJson.result("no_focused_field")
            val existing = if (field.isShowingHintText) "" else field.text?.toString().orEmpty()
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, existing + text)
            }
            val ok = field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            return ToolJson.result(if (ok) "typed" else "no_focused_field")
        } finally {
            recycleQuietly(root)
        }
    }

    /** direction: up/left scroll backward, anything else (default down/right) scrolls forward. */
    fun scroll(direction: String): JSONObject {
        val svc = NovaAccessibilityService.instance ?: return notEnabled()
        val root = svc.rootInActiveWindow ?: return ToolJson.result("nothing_scrollable")
        try {
            val node = findScrollable(root) ?: return ToolJson.result("nothing_scrollable")
            val backward = direction.trim().lowercase() in setOf("up", "left", "back", "backward")
            val action = if (backward) {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
            return ToolJson.result(if (node.performAction(action)) "scrolled" else "nothing_scrollable")
        } finally {
            recycleQuietly(root)
        }
    }

    fun goBack(): JSONObject = global(AccessibilityService.GLOBAL_ACTION_BACK)

    fun goHome(): JSONObject = global(AccessibilityService.GLOBAL_ACTION_HOME)

    private fun global(action: Int): JSONObject {
        val svc = NovaAccessibilityService.instance ?: return notEnabled()
        return ToolJson.result(if (svc.performGlobalAction(action)) "done" else "failed")
    }

    /** True if the app in front belongs to [packageName]. */
    fun isForeground(packageName: String): Boolean {
        val svc = NovaAccessibilityService.instance ?: return false
        val root = svc.rootInActiveWindow ?: return false
        return try {
            root.packageName?.toString() == packageName
        } finally {
            recycleQuietly(root)
        }
    }

    // ---------------- helpers ----------------

    private fun pickBest(matches: List<AccessibilityNodeInfo>, query: String): AccessibilityNodeInfo? {
        val pool = matches.filter { it.isVisibleToUser }.ifEmpty { matches }
        return pool
            .map { node ->
                node to maxOf(
                    TextMatch.score(node.text?.toString(), query),
                    TextMatch.score(node.contentDescription?.toString(), query),
                )
            }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun clickNodeOrAncestor(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        var hops = 0
        while (n != null && hops <= MAX_PARENT_HOPS) {
            if (n.isClickable && n.isEnabled && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
            hops++
        }
        return false
    }

    private suspend fun tapCenter(svc: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return false
        val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return suspendCancellableCoroutine { cont ->
            val dispatched = svc.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null,
            )
            if (!dispatched && cont.isActive) cont.resume(false)
        }
    }

    private fun findScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 800) {
            val node = queue.removeFirst()
            visited++
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.addLast(it) }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun recycleQuietly(node: AccessibilityNodeInfo) {
        try { node.recycle() } catch (_: Exception) {}
    }

    /** Adapter so [ScreenFormatter] can walk real nodes. */
    private class RealNode(private val node: AccessibilityNodeInfo) : UiNode {
        override val text: String? get() = node.text?.toString()
        override val contentDescription: String? get() = node.contentDescription?.toString()
        override val hintText: String? get() = node.hintText?.toString()
        override val isEditable: Boolean get() = node.isEditable
        override val isClickable: Boolean get() = node.isClickable
        override val isPassword: Boolean get() = node.isPassword
        override val childCount: Int get() = node.childCount
        override fun child(index: Int): UiNode? = node.getChild(index)?.let { RealNode(it) }

        @Suppress("DEPRECATION")
        override fun recycle() {
            try { node.recycle() } catch (_: Exception) {}
        }
    }
}
