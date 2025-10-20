package com.care.voice.platform.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

class TtsManager(context: Context, locale: Locale = Locale("ru","RU")) {
    private var tts: TextToSpeech? = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = locale
            tts?.setSpeechRate(0.9f) // скорость (1.0 — стандарт)
        }
    }.apply {
        this?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { onDone?.invoke() }
            override fun onError(utteranceId: String?) {}
        })
    }

    @Volatile private var onDone: (() -> Unit)? = null

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        this.onDone = onDone
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stop() { tts?.stop() }
    fun shutdown() { tts?.stop(); tts?.shutdown(); tts = null }
}
