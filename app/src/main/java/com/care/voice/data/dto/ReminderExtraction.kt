package com.care.voice.data.dto

data class ReminderExtraction(
    val text: String?,
    val delayMillis: Long?,              // ← теперь задержка
    val isRepeating: Boolean?,
    val repeatIntervalMillis: Long?
)


