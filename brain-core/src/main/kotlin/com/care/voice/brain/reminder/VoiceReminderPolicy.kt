package com.care.voice.brain.reminder

interface VoiceReminderSettings {
    val voiceRemindersEnabled: Boolean
    val quietHoursEnabled: Boolean
    val quietHoursStartHour: Int
    val quietHoursEndHour: Int
}

interface Clock {
    fun currentTimeMillis(): Long
    fun currentLocalHour(): Int
}

interface CallStateProvider {
    fun isPhoneCallActive(): Boolean
}

class VoiceReminderPolicy(
    private val settings: VoiceReminderSettings,
    private val clock: Clock,
    private val callState: CallStateProvider
) {
    fun evaluate(text: String, voiceStatus: VoiceDeliveryStatus): VoicePolicyDecision {
        if (voiceStatus != VoiceDeliveryStatus.NOT_REQUESTED) {
            return VoicePolicyDecision.Skip(VoiceSkipReason.ALREADY_DELIVERED)
        }
        if (!settings.voiceRemindersEnabled) {
            return VoicePolicyDecision.Skip(VoiceSkipReason.DISABLED)
        }
        if (text.isBlank()) {
            return VoicePolicyDecision.Skip(VoiceSkipReason.EMPTY_TEXT)
        }
        if (settings.quietHoursEnabled && isQuietHours(clock.currentLocalHour())) {
            return VoicePolicyDecision.Skip(VoiceSkipReason.QUIET_HOURS)
        }
        if (callState.isPhoneCallActive()) {
            return VoicePolicyDecision.Skip(VoiceSkipReason.PHONE_CALL_ACTIVE)
        }
        return VoicePolicyDecision.Proceed
    }

    private fun isQuietHours(hour: Int): Boolean {
        val start = settings.quietHoursStartHour
        val end = settings.quietHoursEndHour
        return if (start > end) {
            hour >= start || hour < end
        } else {
            hour >= start && hour < end
        }
    }
}

sealed interface VoicePolicyDecision {
    data object Proceed : VoicePolicyDecision
    data class Skip(val reason: VoiceSkipReason) : VoicePolicyDecision
}
