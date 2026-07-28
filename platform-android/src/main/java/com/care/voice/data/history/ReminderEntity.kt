package com.care.voice.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.care.voice.brain.reminder.ReminderDeliveryMode
import com.care.voice.brain.reminder.ReminderPrecision
import com.care.voice.brain.reminder.ReminderStatus
import com.care.voice.brain.reminder.VoiceDeliveryStatus

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val triggerAt: Long,
    val isRepeating: Boolean = false,
    val repeatIntervalMillis: Long? = null,
    val status: ReminderStatus = ReminderStatus.SCHEDULING,
    val createdAt: Long = 0L,
    val scheduledAt: Long? = null,
    val triggeredAt: Long? = null,
    val deliveredAt: Long? = null,
    val completedAt: Long? = null,
    val failureReason: String? = null,
    val failureCode: String? = null,
    val failureMessage: String? = null,
    val failedAt: Long? = null,
    val updatedAt: Long = 0L,
    val precision: ReminderPrecision = ReminderPrecision.EXACT,
    val deliveryMode: ReminderDeliveryMode = ReminderDeliveryMode.NOTIFICATION_ONLY,
    val snoozeCount: Int = 0,
    val lastSnoozedAt: Long? = null,
    val voiceDeliveryStatus: VoiceDeliveryStatus = VoiceDeliveryStatus.NOT_REQUESTED,
    val voiceDeliveredAt: Long? = null,
    val voiceSkipReason: String? = null,
    val voiceFailureCode: String? = null,
    val voiceRequestedAt: Long? = null
)
