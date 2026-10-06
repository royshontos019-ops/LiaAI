package com.Lia.assistant.voice

/**
 * Remembers recently seen tool-call ids so the same call is never executed twice
 * (the server can resend a call). Bounded: the oldest id is forgotten first.
 * A blank id cannot be tracked, so it is always treated as new.
 */
class ToolCallDeduplicator(private val capacity: Int = DEFAULT_CAPACITY) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val seen = LinkedHashSet<String>()

    /** True the first time [id] is seen (and records it); false for a repeat. */
    @Synchronized
    fun shouldHandle(id: String): Boolean {
        if (id.isBlank()) return true
        if (!seen.add(id)) return false
        if (seen.size > capacity) {
            val oldest = seen.iterator()
            oldest.next()
            oldest.remove()
        }
        return true
    }

    @Synchronized
    fun clear() = seen.clear()

    val size: Int @Synchronized get() = seen.size

    companion object {
        const val DEFAULT_CAPACITY = 256
    }
}
