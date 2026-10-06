package com.Lia.assistant.voice

enum class Role { USER, ASSISTANT }

data class Turn(val role: Role, val text: String)

/**
 * Rolling window of the last [MAX_TURNS] turns. In memory only: call [clear] when the
 * voice session stops. Thread-safe.
 */
class ConversationMemory {
    private val lock = Any()
    private val turns = ArrayDeque<Turn>()

    fun add(role: Role, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        synchronized(lock) {
            turns.addLast(Turn(role, clean))
            while (turns.size > MAX_TURNS) turns.removeFirst()
        }
    }

    fun clear() = synchronized(lock) { turns.clear() }

    val size: Int get() = synchronized(lock) { turns.size }

    /** "User: ..." / "You: ..." lines, capped to the last [MAX_CHARS] chars; null when empty. */
    fun recap(): String? {
        val joined = synchronized(lock) {
            if (turns.isEmpty()) return null
            turns.joinToString("\n") { t ->
                (if (t.role == Role.USER) "User: " else "You: ") + t.text.replace('\n', ' ')
            }
        }
        if (joined.length <= MAX_CHARS) return joined
        val cut = joined.takeLast(MAX_CHARS)
        // If the cut landed mid-line, drop the partial first line so the recap starts cleanly.
        val cleanStart = joined[joined.length - MAX_CHARS - 1] == '\n'
        if (cleanStart) return cut
        val nl = cut.indexOf('\n')
        return if (nl >= 0) cut.substring(nl + 1) else cut
    }

    companion object {
        const val MAX_TURNS = 12
        const val MAX_CHARS = 1600
    }
}
