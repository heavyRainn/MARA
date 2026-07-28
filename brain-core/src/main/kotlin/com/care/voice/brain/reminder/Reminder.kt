package com.care.voice.brain.reminder

data class Reminder(
    val id: Long,
    val text: String,
    val triggerAtEpochMillis: Long,
    val status: ReminderStatus,
    val precision: ReminderPrecision,
    val deliveryMode: ReminderDeliveryMode = ReminderDeliveryMode.NOTIFICATION_ONLY,
    val isRepeating: Boolean = false,
    val repeatIntervalMillis: Long? = null,
    val snoozeCount: Int = 0
)
