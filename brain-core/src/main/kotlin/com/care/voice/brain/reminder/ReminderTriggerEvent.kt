package com.care.voice.brain.reminder

sealed interface ReminderTriggerEvent {
    data class Triggered(
        val reminderId: Long,
        val text: String,
        val triggerAtEpochMillis: Long
    ) : ReminderTriggerEvent
}
