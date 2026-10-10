package com.Lia.assistant.agent.core

import com.Lia.assistant.agent.core.TaskState.COMPLETED
import com.Lia.assistant.agent.core.TaskState.EXECUTING_ACTION
import com.Lia.assistant.agent.core.TaskState.FAILED
import com.Lia.assistant.agent.core.TaskState.IDLE
import com.Lia.assistant.agent.core.TaskState.NEXT_STEP
import com.Lia.assistant.agent.core.TaskState.OBSERVING
import com.Lia.assistant.agent.core.TaskState.OPENING_APP
import com.Lia.assistant.agent.core.TaskState.REOBSERVE
import com.Lia.assistant.agent.core.TaskState.RESOLVING_TARGET
import com.Lia.assistant.agent.core.TaskState.RETRY
import com.Lia.assistant.agent.core.TaskState.VALIDATING_TARGET
import com.Lia.assistant.agent.core.TaskState.VERIFYING
import com.Lia.assistant.agent.core.TaskState.WAITING_FOR_CONFIRMATION
import com.Lia.assistant.agent.core.TaskState.WAITING_FOR_UI
import com.Lia.assistant.agent.core.TaskState.WAITING_FOR_USER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskStateMachineTest {

    /** The rules, written out by hand. FAILED and CANCELLED are added for every unfinished state below. */
    private val allowed: Map<TaskState, Set<TaskState>> = mapOf(
        IDLE to setOf(OPENING_APP, OBSERVING),
        OPENING_APP to setOf(OBSERVING, WAITING_FOR_USER, RETRY),
        OBSERVING to setOf(RESOLVING_TARGET, WAITING_FOR_USER, NEXT_STEP, RETRY),
        RESOLVING_TARGET to setOf(VALIDATING_TARGET, REOBSERVE, RETRY, WAITING_FOR_USER),
        VALIDATING_TARGET to setOf(EXECUTING_ACTION, WAITING_FOR_CONFIRMATION, REOBSERVE, RETRY),
        EXECUTING_ACTION to setOf(WAITING_FOR_UI, VERIFYING, RETRY, REOBSERVE),
        WAITING_FOR_UI to setOf(VERIFYING, REOBSERVE, RETRY),
        VERIFYING to setOf(NEXT_STEP, RETRY, REOBSERVE, COMPLETED, WAITING_FOR_USER),
        NEXT_STEP to setOf(OBSERVING, OPENING_APP, RESOLVING_TARGET, COMPLETED),
        RETRY to setOf(OBSERVING, REOBSERVE, RESOLVING_TARGET, OPENING_APP),
        REOBSERVE to setOf(OBSERVING, RESOLVING_TARGET, WAITING_FOR_USER),
        WAITING_FOR_CONFIRMATION to setOf(EXECUTING_ACTION, REOBSERVE),
        WAITING_FOR_USER to setOf(OBSERVING, REOBSERVE),
        COMPLETED to emptySet(),
        FAILED to emptySet(),
        TaskState.CANCELLED to emptySet(),
    )

    @Test fun everyPairOfStatesIsLegalOrThrows() {
        var legal = 0
        var illegal = 0
        for (from in TaskState.entries) {
            for (to in TaskState.entries) {
                val expected = !from.isTerminal &&
                    (to in allowed.getValue(from) || to == FAILED || to == TaskState.CANCELLED)
                val machine = TaskStateMachine(from)
                assertEquals("canMoveTo $from -> $to", expected, machine.canMoveTo(to))
                if (expected) {
                    machine.moveTo(to)
                    assertEquals(to, machine.state)
                    legal++
                } else {
                    assertThrows("$from -> $to must throw", IllegalTransitionException::class.java) { machine.moveTo(to) }
                    assertEquals("a refused move changes nothing", from, machine.state)
                    assertTrue(machine.history.isEmpty())
                    illegal++
                }
            }
        }
        assertTrue(legal > 0 && illegal > 0)
        assertEquals(TaskState.entries.size * TaskState.entries.size, legal + illegal)
    }

    @Test fun failedAndCancelledAreReachableFromEveryUnfinishedState() {
        for (from in TaskState.entries.filter { !it.isTerminal }) {
            assertTrue("$from -> FAILED", TaskStateMachine(from).canMoveTo(FAILED))
            assertTrue("$from -> CANCELLED", TaskStateMachine(from).canMoveTo(TaskState.CANCELLED))
        }
    }

    @Test fun finishedStatesNeverLeave() {
        for (end in listOf(COMPLETED, FAILED, TaskState.CANCELLED)) {
            assertTrue(end.isTerminal)
            val machine = TaskStateMachine(end)
            assertTrue(machine.isTerminal)
            for (to in TaskState.entries) assertFalse("$end -> $to", machine.canMoveTo(to))
            assertTrue(TaskStateMachine.legalTargets(end).isEmpty())
        }
    }

    @Test fun anIllegalJumpCarriesBothStates() {
        val machine = TaskStateMachine(IDLE)
        val e = assertThrows(IllegalTransitionException::class.java) { machine.moveTo(COMPLETED) }
        assertEquals(IDLE, e.from)
        assertEquals(COMPLETED, e.to)
    }

    @Test fun historyListsEveryMoveInOrder() {
        val machine = TaskStateMachine()
        machine.moveTo(OPENING_APP, "open")
        machine.moveTo(OBSERVING)
        machine.moveTo(NEXT_STEP, "done")
        machine.moveTo(COMPLETED)
        val history = machine.history
        assertEquals(listOf(1, 2, 3, 4), history.map { it.sequence })
        assertEquals(listOf(IDLE, OPENING_APP, OBSERVING, NEXT_STEP), history.map { it.from })
        assertEquals(listOf(OPENING_APP, OBSERVING, NEXT_STEP, COMPLETED), history.map { it.to })
        assertEquals("open", history.first().note)
        assertTrue(machine.isTerminal)
    }

    @Test fun thePostTapNeedsAnAskStateOnlyWhenTheRunnerUsesOne() {
        // The machine allows both routes; the runner is what forces PUBLISH through confirmation.
        assertTrue(TaskStateMachine(VALIDATING_TARGET).canMoveTo(WAITING_FOR_CONFIRMATION))
        assertTrue(TaskStateMachine(WAITING_FOR_CONFIRMATION).canMoveTo(EXECUTING_ACTION))
        assertFalse("confirmation cannot jump straight to done", TaskStateMachine(WAITING_FOR_CONFIRMATION).canMoveTo(COMPLETED))
        assertFalse("nothing reaches confirmation except a validated target", TaskStateMachine(OBSERVING).canMoveTo(WAITING_FOR_CONFIRMATION))
    }
}
