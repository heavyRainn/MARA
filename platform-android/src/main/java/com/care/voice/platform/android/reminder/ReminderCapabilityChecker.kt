package com.care.voice.platform.android.reminder

interface ReminderCapabilityChecker {
    fun areNotificationsAllowed(): Boolean
    fun canScheduleExactAlarms(): Boolean
}
