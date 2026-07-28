package com.care.voice.brain.reminder

enum class VoiceDeliveryStatus {
    NOT_REQUESTED,
    PENDING,
    SPOKEN,
    SKIPPED,
    FAILED
}

enum class VoiceSkipReason {
    DISABLED,
    QUIET_HOURS,
    PHONE_CALL_ACTIVE,
    AUDIO_FOCUS_DENIED,
    EMPTY_TEXT,
    TTS_UNAVAILABLE,
    ALREADY_DELIVERED
}

sealed interface ReminderSpeechResult {
    data object Spoken : ReminderSpeechResult

    data class Skipped(
        val reason: VoiceSkipReason
    ) : ReminderSpeechResult

    data class Failed(
        val code: String
    ) : ReminderSpeechResult
}

interface ReminderSpeechProvider {
    suspend fun speak(
        reminderId: Long,
        text: String
    ): ReminderSpeechResult
}
