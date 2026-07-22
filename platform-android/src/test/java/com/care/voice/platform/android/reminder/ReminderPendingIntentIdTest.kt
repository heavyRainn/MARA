package com.care.voice.platform.android.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderPendingIntentIdTest {

    @Test
    fun distinctIdsForDistinctReminders() {
        assertEquals(1, 1L.toPendingIntentRequestCode())
        assertEquals(2, 2L.toPendingIntentRequestCode())
        assertEquals(3, 3L.toPendingIntentRequestCode())
    }

    @Test
    fun largeIdUsesStableModulo() {
        val id = 5_000_000_000L
        assertEquals((id % Int.MAX_VALUE).toInt(), id.toPendingIntentRequestCode())
    }
}

internal fun Long.toPendingIntentRequestCode(): Int =
    if (this >= Int.MIN_VALUE.toLong() && this <= Int.MAX_VALUE.toLong()) this.toInt()
    else (this % Int.MAX_VALUE).toInt()
