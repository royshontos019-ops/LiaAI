package com.Lia.assistant.action

/** The few things we need from an accessibility node, so formatting can be unit tested. */
interface UiNode {
    val text: String?
    val contentDescription: String?
    val hintText: String?
    val isEditable: Boolean
    val isClickable: Boolean
    val isPassword: Boolean
    val childCount: Int
    fun child(index: Int): UiNode?
    fun recycle() {}
}

object ScreenFormatter {
    private const val MAX_NODES_VISITED = 800
    private val WHITESPACE = Regex("\\s+")

    /**
     * Breadth-first walk producing unique lines such as "[input] Search" or "[button] Send".
     * Password fields are skipped completely. Stops after [maxElements] lines.
     */
    fun format(root: UiNode, maxElements: Int = 40, maxLabelLength: Int = 40): List<String> {
        val lines = LinkedHashSet<String>()
        val queue = ArrayDeque<UiNode>()
        queue.addLast(root)
        var visited = 0
        while (queue.isNotEmpty() && lines.size < maxElements && visited < MAX_NODES_VISITED) {
            val node = queue.removeFirst()
            visited++
            describe(node, maxLabelLength)?.let { lines += it }
            for (i in 0 until node.childCount) node.child(i)?.let { queue.addLast(it) }
            if (node !== root) node.recycle()
        }
        queue.forEach { if (it !== root) it.recycle() }
        return lines.toList()
    }

    internal fun describe(node: UiNode, maxLabelLength: Int): String? {
        if (node.isPassword) return null
        val raw = listOf(node.text, node.contentDescription, node.hintText)
            .firstOrNull { !it.isNullOrBlank() } ?: return null
        var label = raw.replace(WHITESPACE, " ").trim()
        if (label.length > maxLabelLength) label = label.take(maxLabelLength - 1) + "…"
        val kind = when {
            node.isEditable -> "input"
            node.isClickable -> "button"
            else -> "text"
        }
        return "[$kind] $label"
    }
}

/** How well an on-screen label matches what the user asked to tap. */
object TextMatch {
    /** 3 = exact, 2 = starts with, 1 = contains, 0 = no match (case-insensitive). */
    fun score(label: String?, query: String): Int {
        val l = label?.trim().orEmpty()
        val q = query.trim()
        if (l.isEmpty() || q.isEmpty()) return 0
        return when {
            l.equals(q, ignoreCase = true) -> 3
            l.startsWith(q, ignoreCase = true) -> 2
            l.contains(q, ignoreCase = true) -> 1
            else -> 0
        }
    }
}
