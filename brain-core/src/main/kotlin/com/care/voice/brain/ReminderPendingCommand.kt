package com.care.voice.brain

/**
 * In-process reminder confirmation payload (serialized into [pending.PendingAction.payload] for persistence).
 */
sealed interface ReminderPendingCommand {
    data class ScheduleReminder(
        val title: String,
        val triggerAtEpochMillis: Long,
        val isRepeating: Boolean,
        val repeatIntervalMillis: Long?,
        val humanReadableTime: String,
        val precision: com.care.voice.brain.reminder.ReminderPrecision
    ) : ReminderPendingCommand
}
