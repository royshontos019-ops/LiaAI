package com.Lia.assistant.agent.core

enum class TaskState {
    IDLE,
    OPENING_APP,
    OBSERVING,
    RESOLVING_TARGET,
    VALIDATING_TARGET,
    EXECUTING_ACTION,
    WAITING_FOR_UI,
    VERIFYING,
    NEXT_STEP,
    RETRY,
    REOBSERVE,
    WAITING_FOR_CONFIRMATION,
    WAITING_FOR_USER,
    COMPLETED,
    FAILED,
    CANCELLED,
    ;

    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED
}

class IllegalTransitionException(val from: TaskState, val to: TaskState) :
    IllegalStateException("Illegal task transition: $from -> $to")

data class StateTransition(val sequence: Int, val from: TaskState, val to: TaskState, val note: String?)

/**
 * The life of one agent task. [moveTo] throws on a move that is not allowed, so a bug can never
 * quietly skip a step (for example jumping to the irreversible tap without asking first).
 * FAILED and CANCELLED can be reached from every state that is not finished; a finished state
 * (COMPLETED, FAILED, CANCELLED) can never be left.
 */
class TaskStateMachine(initial: TaskState = TaskState.IDLE) {
    private val lock = Any()
    private val log = ArrayList<StateTransition>()

    @Volatile
    var state: TaskState = initial
        private set

    val isTerminal: Boolean get() = state.isTerminal

    /** Every move made so far, oldest first. */
    val history: List<StateTransition> get() = synchronized(lock) { log.toList() }

    fun canMoveTo(target: TaskState): Boolean = isLegal(state, target)

    fun moveTo(target: TaskState, note: String? = null) {
        synchronized(lock) {
            val from = state
            if (!isLegal(from, target)) throw IllegalTransitionException(from, target)
            log += StateTransition(log.size + 1, from, target, note)
            state = target
        }
    }

    companion object {
        private val FORWARD: Map<TaskState, Set<TaskState>> = mapOf(
            TaskState.IDLE to setOf(TaskState.OPENING_APP, TaskState.OBSERVING),
            TaskState.OPENING_APP to setOf(TaskState.OBSERVING, TaskState.WAITING_FOR_USER, TaskState.RETRY),
            TaskState.OBSERVING to setOf(
                TaskState.RESOLVING_TARGET, TaskState.WAITING_FOR_USER, TaskState.NEXT_STEP, TaskState.RETRY,
            ),
            TaskState.RESOLVING_TARGET to setOf(
                TaskState.VALIDATING_TARGET, TaskState.REOBSERVE, TaskState.RETRY, TaskState.WAITING_FOR_USER,
            ),
            TaskState.VALIDATING_TARGET to setOf(
                TaskState.EXECUTING_ACTION, TaskState.WAITING_FOR_CONFIRMATION, TaskState.REOBSERVE, TaskState.RETRY,
            ),
            TaskState.EXECUTING_ACTION to setOf(
                TaskState.WAITING_FOR_UI, TaskState.VERIFYING, TaskState.RETRY, TaskState.REOBSERVE,
            ),
            TaskState.WAITING_FOR_UI to setOf(TaskState.VERIFYING, TaskState.REOBSERVE, TaskState.RETRY),
            TaskState.VERIFYING to setOf(
                TaskState.NEXT_STEP, TaskState.RETRY, TaskState.REOBSERVE, TaskState.COMPLETED, TaskState.WAITING_FOR_USER,
            ),
            TaskState.NEXT_STEP to setOf(
                TaskState.OBSERVING, TaskState.OPENING_APP, TaskState.RESOLVING_TARGET, TaskState.COMPLETED,
            ),
            TaskState.RETRY to setOf(
                TaskState.OBSERVING, TaskState.REOBSERVE, TaskState.RESOLVING_TARGET, TaskState.OPENING_APP,
            ),
            TaskState.REOBSERVE to setOf(TaskState.OBSERVING, TaskState.RESOLVING_TARGET, TaskState.WAITING_FOR_USER),
            TaskState.WAITING_FOR_CONFIRMATION to setOf(TaskState.EXECUTING_ACTION, TaskState.REOBSERVE),
            TaskState.WAITING_FOR_USER to setOf(TaskState.OBSERVING, TaskState.REOBSERVE),
            TaskState.COMPLETED to emptySet(),
            TaskState.FAILED to emptySet(),
            TaskState.CANCELLED to emptySet(),
        )

        /** All states reachable in one move from [from]. */
        fun legalTargets(from: TaskState): Set<TaskState> {
            if (from.isTerminal) return emptySet()
            return (FORWARD[from] ?: emptySet()) + TaskState.FAILED + TaskState.CANCELLED
        }

        fun isLegal(from: TaskState, to: TaskState): Boolean = to in legalTargets(from)
    }
}
