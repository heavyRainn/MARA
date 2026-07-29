package com.care.voice.platform.android.reminder.voice

import android.content.Context
import android.telephony.TelephonyManager
import com.care.voice.brain.reminder.Clock
import com.care.voice.brain.reminder.CallStateProvider
import com.care.voice.brain.reminder.VoiceReminderSettings
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class DefaultVoiceReminderSettings : VoiceReminderSettings {
    override val voiceRemindersEnabled: Boolean = true
    override val quietHoursEnabled: Boolean = false
    override val quietHoursStartHour: Int = 22
    override val quietHoursEndHour: Int = 8
}

class SystemClockAdapter : Clock {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun currentLocalHour(): Int {
        val cal = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault())
        return cal.get(Calendar.HOUR_OF_DAY)
    }
}

class AndroidCallStateProvider(
    private val context: Context
) : CallStateProvider {
    override fun isPhoneCallActive(): Boolean {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return false
        @Suppress("DEPRECATION")
        val callState = telephony.callState
        return callState != TelephonyManager.CALL_STATE_IDLE
    }
}
