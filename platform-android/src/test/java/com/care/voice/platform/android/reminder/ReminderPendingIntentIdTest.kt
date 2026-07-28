package com.care.voice.platform.android.reminder

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderPendingIntentIdTest {

    @Test
    fun distinctCodesForDistinctReminders() {
        val id1 = 1L
        val id2 = 2L
        assertNotEquals(
            ReminderPendingIntentFactory.requestCode(1_000_000, id1),
            ReminderPendingIntentFactory.requestCode(1_000_000, id2)
        )
        assertNotEquals(
            ReminderPendingIntentFactory.requestCode(1_000_000, id1),
            ReminderPendingIntentFactory.requestCode(3_000_000, id1)
        )
    }

    @Test
    fun distinctUrisForAlarmCompleteSnooze() {
        val id = 42L
        assertNotEquals(
            ReminderPendingIntentFactory.reminderUriString(id, "alarm"),
            ReminderPendingIntentFactory.reminderUriString(id, "complete")
        )
        assertNotEquals(
            ReminderPendingIntentFactory.reminderUriString(id, "complete"),
            ReminderPendingIntentFactory.reminderUriString(id, "snooze")
        )
    }

    @Test
    fun largeIdUsesStableHash() {
        val id = 5_000_000_000L
        val code = ReminderPendingIntentFactory.requestCode(1_000_000, id)
        assertEquals((31 * 1_000_000 + id.hashCode()) and 0x7FFFFFFF, code)
    }
}
