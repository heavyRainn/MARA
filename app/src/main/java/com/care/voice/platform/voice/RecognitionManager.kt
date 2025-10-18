package com.care.voice.platform.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow
import java.util.Locale

sealed interface RecognitionEvent {
    data class Partial(val text: String, val rms: Float) : RecognitionEvent
    data class Final(val text: String) : RecognitionEvent
    data class Error(val code: Int, val message: String) : RecognitionEvent
    data class Rms(val value: Float) : RecognitionEvent
    object Ready : RecognitionEvent
    object End : RecognitionEvent
}

class RecognitionManager(private val context: Context) {

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun listen(locale: Locale): Flow<RecognitionEvent> = callbackFlow {
        if (!isAvailable()) {
            trySend(RecognitionEvent.Error(-1, "Служба распознавания недоступна")); close(); return@callbackFlow
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите")
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { trySend(RecognitionEvent.Ready) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) { trySend(RecognitionEvent.Rms(rmsdB)) }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { /* ждём onResults/onError */ }
            override fun onError(error: Int) {
                trySend(RecognitionEvent.Error(error, mapError(error))); trySend(RecognitionEvent.End); close()
            }
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) trySend(RecognitionEvent.Final(text))
                trySend(RecognitionEvent.End); close()
            }
            override fun onPartialResults(partialResults: Bundle) {
                val text = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                trySend(RecognitionEvent.Partial(text, 0f))
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)

        awaitClose {
            try { recognizer.cancel() } catch (_:Throwable) {}
            recognizer.destroy()
        }
    }

    private fun mapError(code: Int) = when (code) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Сеть: таймаут"
        SpeechRecognizer.ERROR_NETWORK -> "Проблема сети"
        SpeechRecognizer.ERROR_AUDIO -> "Проблема аудио"
        SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера"
        SpeechRecognizer.ERROR_CLIENT -> "Клиентская ошибка"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Нет речи"
        SpeechRecognizer.ERROR_NO_MATCH -> "Не удалось распознать"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешения"
        else -> "Неизвестная ошибка ($code)"
    }
}
