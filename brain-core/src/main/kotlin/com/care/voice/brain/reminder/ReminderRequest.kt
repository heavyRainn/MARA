package com.care.voice.brain.reminder

data class ReminderRequest(
    val text: String,
    val triggerAtMillis: Long,
    val isRepeating: Boolean,
    val repeatIntervalMillis: Long?
)

sealed interface ReminderScheduleResult {
    data class Success(val reminderId: Long) : ReminderScheduleResult
    data class Failure(val message: String) : ReminderScheduleResult
}

interface ReminderScheduler {
    suspend fun schedule(request: ReminderRequest): ReminderScheduleResult
}
