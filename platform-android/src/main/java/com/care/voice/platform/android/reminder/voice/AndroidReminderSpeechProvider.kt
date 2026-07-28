package com.care.voice.platform.android.reminder.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.care.voice.brain.reminder.ReminderSpeechProvider
import com.care.voice.brain.reminder.ReminderSpeechResult
import com.care.voice.brain.reminder.VoiceSkipReason
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class PlatformAndroidTtsEngine(
    private val context: Context
) : AndroidTtsEngine {

    private var tts: TextToSpeech? = null
    private val ruLocale: Locale = Locale.forLanguageTag("ru-RU")

    override suspend fun initialize(): TtsInitResult = suspendCancellableCoroutine { cont ->
        val resumed = AtomicBoolean(false)
        fun resumeOnce(result: TtsInitResult) {
            if (resumed.compareAndSet(false, true)) cont.resume(result)
        }

        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                resumeOnce(TtsInitResult.Unavailable)
                return@TextToSpeech
            }
            val engine = tts ?: run {
                resumeOnce(TtsInitResult.Unavailable)
                return@TextToSpeech
            }
            val localeResult = engine.setLanguage(ruLocale)
            if (localeResult == TextToSpeech.LANG_MISSING_DATA ||
                localeResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                resumeOnce(TtsInitResult.LocaleUnavailable(ruLocale.toLanguageTag()))
                return@TextToSpeech
            }
            selectBestRussianVoice(engine)
            engine.setSpeechRate(0.92f)
            engine.setPitch(0.98f)
            resumeOnce(TtsInitResult.Success)
        }

        cont.invokeOnCancellation {
            shutdown()
        }
    }

    override suspend fun speak(text: String, utteranceId: String): TtsSpeakResult =
        suspendCancellableCoroutine { cont ->
            val engine = tts ?: run {
                cont.resume(TtsSpeakResult.Error("tts_not_initialized"))
                return@suspendCancellableCoroutine
            }
            val resumed = AtomicBoolean(false)
            fun resumeOnce(result: TtsSpeakResult) {
                if (resumed.compareAndSet(false, true)) cont.resume(result)
            }

            val targetId = utteranceId
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit

                override fun onDone(id: String?) {
                    if (id == targetId) resumeOnce(TtsSpeakResult.Done)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == targetId) resumeOnce(TtsSpeakResult.Error("legacy_error"))
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == targetId) resumeOnce(TtsSpeakResult.Error("error_$errorCode"))
                }
            })

            val queued = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (queued == TextToSpeech.ERROR) {
                resumeOnce(TtsSpeakResult.Error("speak_queue_failed"))
            }

            cont.invokeOnCancellation {
                engine.stop()
            }
        }

    override fun stop() {
        tts?.stop()
    }

    override fun shutdown() {
        tts?.shutdown()
        tts = null
    }

    private fun selectBestRussianVoice(engine: TextToSpeech) {
        val voices = engine.voices ?: return
        val best = voices
            .filter { it.locale.language == ruLocale.language }
            .maxByOrNull { voiceScore(it) }
        best?.let { engine.voice = it }
    }

    private fun voiceScore(voice: Voice): Int {
        var score = 0
        if (voice.locale.country.equals("RU", ignoreCase = true)) score += 2
        if (!voice.isNetworkConnectionRequired) score += 1
        return score
    }
}

class AndroidReminderSpeechProvider(
    private val ttsEngine: AndroidTtsEngine,
    private val audioFocus: AudioFocusController,
    private val speakTimeoutMs: Long = 30_000L
) : ReminderSpeechProvider {

    override suspend fun speak(reminderId: Long, text: String): ReminderSpeechResult {
        if (text.isBlank()) {
            return ReminderSpeechResult.Skipped(VoiceSkipReason.EMPTY_TEXT)
        }

        val initResult = ttsEngine.initialize()
        when (initResult) {
            TtsInitResult.Unavailable -> return ReminderSpeechResult.Skipped(VoiceSkipReason.TTS_UNAVAILABLE)
            is TtsInitResult.LocaleUnavailable -> return ReminderSpeechResult.Skipped(VoiceSkipReason.TTS_UNAVAILABLE)
            TtsInitResult.Success -> Unit
        }

        val focusGranted = audioFocus.requestTransientFocus()
        if (!focusGranted) {
            ttsEngine.shutdown()
            return ReminderSpeechResult.Skipped(VoiceSkipReason.AUDIO_FOCUS_DENIED)
        }

        return try {
            val utteranceId = "reminder-voice-$reminderId-${System.currentTimeMillis()}"
            val speakResult = withTimeoutOrNull(speakTimeoutMs) {
                ttsEngine.speak(text, utteranceId)
            } ?: TtsSpeakResult.Timeout

            when (speakResult) {
                TtsSpeakResult.Done -> ReminderSpeechResult.Spoken
                TtsSpeakResult.Timeout -> ReminderSpeechResult.Failed("tts_timeout")
                TtsSpeakResult.AudioFocusDenied -> ReminderSpeechResult.Skipped(VoiceSkipReason.AUDIO_FOCUS_DENIED)
                is TtsSpeakResult.Error -> ReminderSpeechResult.Failed(speakResult.code)
            }
        } finally {
            ttsEngine.stop()
            ttsEngine.shutdown()
            audioFocus.abandonFocus()
        }
    }
}
