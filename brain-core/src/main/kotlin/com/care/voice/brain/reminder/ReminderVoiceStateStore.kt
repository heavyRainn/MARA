package com.care.voice.brain.reminder

interface ReminderVoiceStateStore {
    suspend fun getStatus(reminderId: Long): VoiceDeliveryStatus
    suspend fun tryMarkPending(reminderId: Long, nowEpochMillis: Long): Boolean
    suspend fun markSpoken(reminderId: Long, nowEpochMillis: Long)
    suspend fun markSkipped(reminderId: Long, reason: VoiceSkipReason, nowEpochMillis: Long)
    suspend fun markFailed(reminderId: Long, code: String, nowEpochMillis: Long)
    suspend fun resetForNewDeliveryCycle(reminderId: Long)
}
