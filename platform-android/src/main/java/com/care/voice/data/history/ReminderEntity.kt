package com.care.voice.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val triggerAt: Long,
    val isRepeating: Boolean = false,
    val repeatIntervalMillis: Long? = null
)
