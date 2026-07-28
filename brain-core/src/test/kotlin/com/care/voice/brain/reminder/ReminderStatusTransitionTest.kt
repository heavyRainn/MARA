package com.care.voice.brain.reminder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderStatusTransitionTest {

    @Test
    fun skipsDuplicateTriggerForTerminalAndPostTriggerStates() {
        assertTrue(ReminderStatusTransition.shouldSkipAlarmTrigger(ReminderStatus.TRIGGERED))
        assertTrue(ReminderStatusTransition.shouldSkipAlarmTrigger(ReminderStatus.DELIVERED))
        assertTrue(ReminderStatusTransition.shouldSkipAlarmTrigger(ReminderStatus.COMPLETED))
        assertTrue(ReminderStatusTransition.shouldSkipAlarmTrigger(ReminderStatus.CANCELLED))
        assertTrue(ReminderStatusTransition.shouldSkipAlarmTrigger(ReminderStatus.FAILED))
        assertFalse(ReminderStatusTransition.shouldSkipAlarmTrigger(ReminderStatus.SCHEDULED))
    }

    @Test
    fun rejectsReverseTransitions() {
        assertFalse(ReminderStatusTransition.canTransition(ReminderStatus.DELIVERED, ReminderStatus.SCHEDULED))
        assertFalse(ReminderStatusTransition.canTransition(ReminderStatus.COMPLETED, ReminderStatus.TRIGGERED))
        assertFalse(ReminderStatusTransition.canTransition(ReminderStatus.FAILED, ReminderStatus.SCHEDULED))
    }

    @Test
    fun allowsForwardFlow() {
        assertTrue(ReminderStatusTransition.canTransition(ReminderStatus.SCHEDULED, ReminderStatus.TRIGGERED))
        assertTrue(ReminderStatusTransition.canTransition(ReminderStatus.TRIGGERED, ReminderStatus.DELIVERED))
        assertTrue(ReminderStatusTransition.canTransition(ReminderStatus.DELIVERED, ReminderStatus.COMPLETED))
        assertTrue(ReminderStatusTransition.canTransition(ReminderStatus.SCHEDULING, ReminderStatus.SCHEDULED))
    }

    @Test
    fun completeAndSnoozeRules() {
        assertTrue(ReminderStatusTransition.canComplete(ReminderStatus.DELIVERED))
        assertTrue(ReminderStatusTransition.canSnooze(ReminderStatus.DELIVERED))
        assertFalse(ReminderStatusTransition.canComplete(ReminderStatus.COMPLETED))
        assertTrue(ReminderStatusTransition.isSnoozeInProgress(ReminderStatus.SCHEDULING))
    }
}
