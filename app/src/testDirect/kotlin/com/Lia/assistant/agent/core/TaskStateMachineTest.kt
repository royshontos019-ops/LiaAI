package com.Lia.assistant.agent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TaskStateMachineTest {

    /** Written out by hand on purpose: if someone changes the machine, this must be changed too. */
    private val expectedForward: Map<TaskState, Set<TaskState>> = mapOf(
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
    )

    private fun expectedTargets(from: TaskState): Set<TaskState> =
        if (from.isTerminal) emptySet()
        else (expectedForward.getValue(from) + TaskState.FAILED + TaskState.CANCELLED)

    private fun movesIllegally(from: TaskState, to: TaskState): Boolean {
        val machine = TaskStateMachine(from)
        return try {
            machine.moveTo(to)
            false
        } catch (e: IllegalTransitionException) {
            assertEquals(from, e.from)
            assertEquals(to, e.to)
            assertEquals("a refused move must not change the state", from, machine.state)
            assertTrue("a refused move must not be recorded", machine.history.isEmpty())
            true
        }
    }

    @Test
    fun startsIdleWithNoHistory() {
        val machine = TaskStateMachine()
        assertEquals(TaskState.IDLE, machine.state)
        assertTrue(machine.history.isEmpty())
        assertFalse(machine.isTerminal)
    }

    @Test
    fun everyLegalTransitionIsAccepted() {
        for (from in TaskState.entries) {
            for (to in expectedTargets(from)) {
                val machine = TaskStateMachine(from)
                machine.moveTo(to, "test")
                assertEquals("$from -> $to", to, machine.state)
                assertEquals(1, machine.history.size)
                assertEquals(from, machine.history.single().from)
                assertEquals(to, machine.history.single().to)
            }
        }
    }

    @Test
    fun everyIllegalTransitionThrows() {
        for (from in TaskState.entries) {
            val legal = expectedTargets(from)
            for (to in TaskState.entries) {
                if (to in legal) continue
                assertTrue("$from -> $to should be illegal", movesIllegally(from, to))
            }
        }
    }

    @Test
    fun machineAndTheHandWrittenTableAgree() {
        for (from in TaskState.entries) {
            assertEquals("targets of $from", expectedTargets(from), TaskStateMachine.legalTargets(from))
        }
    }

    @Test
    fun failedAndCancelledAreReachableFromEveryUnfinishedState() {
        for (from in TaskState.entries.filter { !it.isTerminal }) {
            for (end in listOf(TaskState.FAILED, TaskState.CANCELLED)) {
                val machine = TaskStateMachine(from)
                machine.moveTo(end)
                assertEquals(end, machine.state)
                assertTrue(machine.isTerminal)
            }
        }
    }

    @Test
    fun finishedStatesNeverLeave() {
        for (finished in listOf(TaskState.COMPLETED, TaskState.FAILED, TaskState.CANCELLED)) {
            for (to in TaskState.entries) {
                assertTrue("$finished -> $to must be refused", movesIllegally(finished, to))
            }
        }
    }

    @Test
    fun movingToTheSameStateIsRefused() {
        for (state in TaskState.entries) {
            assertTrue("$state -> $state", movesIllegally(state, state))
        }
    }

    @Test
    fun theIrreversibleStepCannotBeReachedWithoutAskingFirst() {
        // Everything before the action can reach WAITING_FOR_CONFIRMATION only from VALIDATING_TARGET,
        // and EXECUTING_ACTION can be entered from WAITING_FOR_CONFIRMATION or VALIDATING_TARGET.
        val canEnterConfirmation = TaskState.entries.filter { TaskState.WAITING_FOR_CONFIRMATION in TaskStateMachine.legalTargets(it) }
        assertEquals(
            setOf(TaskState.VALIDATING_TARGET),
            canEnterConfirmation.filter { it != TaskState.WAITING_FOR_CONFIRMATION }.toSet(),
        )
        assertFalse(TaskStateMachine.isLegal(TaskState.IDLE, TaskState.EXECUTING_ACTION))
        assertFalse(TaskStateMachine.isLegal(TaskState.OBSERVING, TaskState.EXECUTING_ACTION))
        assertFalse(TaskStateMachine.isLegal(TaskState.WAITING_FOR_USER, TaskState.EXECUTING_ACTION))
    }

    @Test
    fun aBlockerPausesAtWaitingForUserAndCanResume() {
        val machine = TaskStateMachine()
        machine.moveTo(TaskState.OPENING_APP)
        machine.moveTo(TaskState.OBSERVING)
        machine.moveTo(TaskState.WAITING_FOR_USER, "login screen")
        assertEquals(TaskState.WAITING_FOR_USER, machine.state)
        assertFalse(machine.canMoveTo(TaskState.EXECUTING_ACTION))
        machine.moveTo(TaskState.OBSERVING, "resumed")
        assertEquals(TaskState.OBSERVING, machine.state)
    }

    @Test
    fun historyKeepsEveryMoveInOrderWithNotes() {
        val machine = TaskStateMachine()
        machine.moveTo(TaskState.OPENING_APP, "open")
        machine.moveTo(TaskState.OBSERVING)
        machine.moveTo(TaskState.RESOLVING_TARGET, "find share")
        machine.moveTo(TaskState.FAILED, "not found")

        val history = machine.history
        assertEquals(listOf(1, 2, 3, 4), history.map { it.sequence })
        assertEquals(
            listOf(TaskState.OPENING_APP, TaskState.OBSERVING, TaskState.RESOLVING_TARGET, TaskState.FAILED),
            history.map { it.to },
        )
        assertEquals(TaskState.IDLE, history.first().from)
        assertEquals("find share", history[2].note)
        assertEquals(null, history[1].note)
    }

    @Test
    fun aFullHappyPathWalksThroughConfirmation() {
        val machine = TaskStateMachine()
        val path = listOf(
            TaskState.OPENING_APP, TaskState.OBSERVING, TaskState.RESOLVING_TARGET, TaskState.VALIDATING_TARGET,
            TaskState.WAITING_FOR_CONFIRMATION, TaskState.EXECUTING_ACTION, TaskState.WAITING_FOR_UI,
            TaskState.VERIFYING, TaskState.COMPLETED,
        )
        path.forEach { machine.moveTo(it) }
        assertEquals(TaskState.COMPLETED, machine.state)
        assertTrue(machine.isTerminal)
        assertEquals(path.size, machine.history.size)
    }

    @Test
    fun illegalMoveMessageNamesBothStates() {
        try {
            TaskStateMachine().moveTo(TaskState.COMPLETED)
            fail("expected an IllegalTransitionException")
        } catch (e: IllegalTransitionException) {
            assertTrue(e.message!!.contains("IDLE"))
            assertTrue(e.message!!.contains("COMPLETED"))
        }
    }
}
