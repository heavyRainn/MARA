package com.care.voice.brain.reminder

data class ReminderRequest(
    val text: String,
    val triggerAtMillis: Long,
    val precision: ReminderPrecision,
    val isRepeating: Boolean,
    val repeatIntervalMillis: Long?,
    val deliveryMode: ReminderDeliveryMode = ReminderDeliveryMode.NOTIFICATION_ONLY
)

sealed interface ScheduleReminderResult {
    data class Success(
        val reminderId: Long,
        val scheduledAtEpochMillis: Long,
        val precision: ReminderPrecision
    ) : ScheduleReminderResult

    data object NotificationPermissionRequired : ScheduleReminderResult

    data object ExactAlarmPermissionRequired : ScheduleReminderResult

    data class InvalidTime(
        val reason: String
    ) : ScheduleReminderResult

    data class Failure(
        val reason: String
    ) : ScheduleReminderResult
}

interface ReminderScheduler {
    suspend fun schedule(request: ReminderRequest): ScheduleReminderResult
}
