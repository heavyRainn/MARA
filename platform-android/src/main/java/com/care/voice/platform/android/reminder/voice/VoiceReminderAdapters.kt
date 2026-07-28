package com.care.voice.platform.android.reminder.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.telephony.TelephonyManager
import com.care.voice.brain.reminder.Clock
import com.care.voice.brain.reminder.CallStateProvider
import com.care.voice.brain.reminder.VoiceReminderSettings
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

interface AndroidTtsEngine {
    suspend fun initialize(): TtsInitResult
    suspend fun speak(text: String, utteranceId: String): TtsSpeakResult
    fun stop()
    fun shutdown()
}

sealed interface TtsInitResult {
    data object Success : TtsInitResult
    data object Unavailable : TtsInitResult
    data class LocaleUnavailable(val localeTag: String) : TtsInitResult
}

sealed interface TtsSpeakResult {
    data object Done : TtsSpeakResult
    data class Error(val code: String) : TtsSpeakResult
    data object Timeout : TtsSpeakResult
    data object AudioFocusDenied : TtsSpeakResult
}

interface AudioFocusController {
    fun requestTransientFocus(): Boolean
    fun abandonFocus()
}

class DefaultVoiceReminderSettings : VoiceReminderSettings {
    override val voiceRemindersEnabled: Boolean = true
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

class AndroidAudioFocusController(
    private val context: Context
) : AudioFocusController {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    override fun requestTransientFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    override fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        focusRequest = null
    }
}
