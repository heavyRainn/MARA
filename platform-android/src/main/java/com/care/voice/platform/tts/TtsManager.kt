package com.care.voice.platform.tts

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class TtsCallbacks(
    val onStart: (() -> Unit)? = null,
    val onDone: (() -> Unit)? = null,
    val onError: (() -> Unit)? = null
)

class TtsManager(
    context: Context,
    locale: Locale = Locale.forLanguageTag("ru-RU")
) {
    private var tts: TextToSpeech? = null
    private val callbacksByUtteranceId = ConcurrentHashMap<String, TtsCallbacks>()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = locale
                tts?.setSpeechRate(0.9f)
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                utteranceId?.let { id ->
                    callbacksByUtteranceId[id]?.onStart?.invoke()
                }
            }

            override fun onDone(utteranceId: String?) {
                utteranceId?.let { id ->
                    callbacksByUtteranceId.remove(id)?.onDone?.invoke()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                utteranceId?.let { id ->
                    callbacksByUtteranceId.remove(id)?.onError?.invoke()
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                onError(utteranceId)
            }
        })
    }

    fun speak(
        text: String,
        utteranceId: String,
        callbacks: TtsCallbacks = TtsCallbacks()
    ) {
        callbacksByUtteranceId[utteranceId] = callbacks
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    fun stop() {
        tts?.stop()
        callbacksByUtteranceId.clear()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
