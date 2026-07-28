package com.care.voice.platform.android.reminder

object ReminderNotificationIds {
    fun forReminder(reminderId: Long): Int =
        if (reminderId >= Int.MIN_VALUE.toLong() && reminderId <= Int.MAX_VALUE.toLong()) {
            reminderId.toInt()
        } else {
            (reminderId % Int.MAX_VALUE).toInt()
        }
}
