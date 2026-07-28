package com.care.voice.data.history

import androidx.room.TypeConverter
import com.care.voice.brain.reminder.ReminderDeliveryMode
import com.care.voice.brain.reminder.ReminderPrecision
import com.care.voice.brain.reminder.ReminderStatus
import com.care.voice.brain.reminder.VoiceDeliveryStatus

class ReminderConverters {
    @TypeConverter
    fun fromStatus(value: ReminderStatus): String = value.name

    @TypeConverter
    fun toStatus(value: String): ReminderStatus = ReminderStatus.valueOf(value)

    @TypeConverter
    fun fromPrecision(value: ReminderPrecision): String = value.name

    @TypeConverter
    fun toPrecision(value: String): ReminderPrecision = ReminderPrecision.valueOf(value)

    @TypeConverter
    fun fromDeliveryMode(value: ReminderDeliveryMode): String = value.name

    @TypeConverter
    fun toDeliveryMode(value: String): ReminderDeliveryMode = ReminderDeliveryMode.valueOf(value)

    @TypeConverter
    fun fromVoiceDeliveryStatus(value: VoiceDeliveryStatus): String = value.name

    @TypeConverter
    fun toVoiceDeliveryStatus(value: String): VoiceDeliveryStatus = VoiceDeliveryStatus.valueOf(value)
}
