package com.care.voice.brain.speech

import com.care.voice.brain.reminder.ReminderSpeechProvider
import com.care.voice.brain.reminder.ReminderSpeechResult
import com.care.voice.brain.reminder.VoiceSkipReason

class ReminderSpeechCoordinator(
    private val playbackCoordinator: SpeechPlaybackCoordinator,
) : ReminderSpeechProvider {

    override suspend fun speak(reminderId: Long, text: String): ReminderSpeechResult {
        val requestId = "reminder-$reminderId-${System.currentTimeMillis()}"
        val request = SpeechRequest(
            requestId = requestId,
            text = text,
            purpose = SpeechPurpose.REMINDER,
            playbackMode = SpeechPlaybackMode.QUEUE,
        )
        return when (val result = playbackCoordinator.speak(request)) {
            is SpeechResult.Spoken -> ReminderSpeechResult.Spoken
            is SpeechResult.Skipped -> ReminderSpeechResult.Skipped(mapSkipReason(result.reason))
            is SpeechResult.Failed -> ReminderSpeechResult.Failed(result.primaryFailure.name)
            is SpeechResult.Cancelled -> ReminderSpeechResult.Skipped(VoiceSkipReason.DISABLED)
        }
    }

    private fun mapSkipReason(reason: SpeechSkipReason): VoiceSkipReason = when (reason) {
        SpeechSkipReason.EMPTY_TEXT -> VoiceSkipReason.EMPTY_TEXT
        SpeechSkipReason.VOICE_DISABLED,
        SpeechSkipReason.AUTO_READ_DISABLED,
        SpeechSkipReason.REMINDER_VOICE_DISABLED,
        -> VoiceSkipReason.DISABLED
        SpeechSkipReason.REMINDER_QUIET_HOURS -> VoiceSkipReason.QUIET_HOURS
        SpeechSkipReason.PHONE_CALL_ACTIVE -> VoiceSkipReason.PHONE_CALL_ACTIVE
        SpeechSkipReason.REMINDER_BUSY_TIMEOUT -> VoiceSkipReason.ALREADY_DELIVERED
        SpeechSkipReason.STALE_REQUEST,
        SpeechSkipReason.CANCELLED,
        -> VoiceSkipReason.ALREADY_DELIVERED
    }
}
