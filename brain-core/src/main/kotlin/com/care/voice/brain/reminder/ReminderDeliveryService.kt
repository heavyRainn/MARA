package com.care.voice.brain.reminder

sealed interface ReminderDeliveryResult {
    data object Delivered : ReminderDeliveryResult
    data class Failed(val reason: String) : ReminderDeliveryResult
}

interface ReminderDeliveryService {
    suspend fun deliver(reminder: Reminder): ReminderDeliveryResult
}
