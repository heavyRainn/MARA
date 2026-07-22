package com.care.voice.platform.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

sealed interface RecognitionEvent {
    data class Partial(val text: String, val rms: Float) : RecognitionEvent
    data class Final(val text: String) : RecognitionEvent
    data class Error(val code: Int, val message: String) : RecognitionEvent
    data class Rms(val value: Float) : RecognitionEvent
    data object Ready : RecognitionEvent
    data object End : RecognitionEvent
}

class RecognitionManager(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var recognizer: SpeechRecognizer? = null

    /** Active flow emitter; null when no session is running. */
    private val activeEmitter = AtomicReference<((RecognitionEvent) -> Unit)?>(null)
    private var rmsLogCounter = 0

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            YasnaSpeechLog.d("onReadyForSpeech")
            emit(RecognitionEvent.Ready)
        }

        override fun onBeginningOfSpeech() {
            YasnaSpeechLog.d("onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (rmsLogCounter++ % 15 == 0) {
                YasnaSpeechLog.d("onRmsChanged rms=${"%.1f".format(rmsdB)}")
            }
            emit(RecognitionEvent.Rms(rmsdB))
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            YasnaSpeechLog.d("onEndOfSpeech")
        }

        override fun onError(error: Int) {
            YasnaSpeechLog.w("onError ${YasnaSpeechLog.decodeError(error)}")
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                mainHandler.post {
                    YasnaSpeechLog.d("cancel (ERROR_RECOGNIZER_BUSY recovery)")
                    recognizer?.cancel()
                }
            }
            emit(RecognitionEvent.Error(error, mapError(error)))
            emit(RecognitionEvent.End)
            activeEmitter.set(null)
        }

        override fun onResults(results: Bundle) {
            val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull().orEmpty()
            YasnaSpeechLog.dRecognized("onResults count=${matches?.size ?: 0}", text)
            if (text.isNotBlank()) emit(RecognitionEvent.Final(text))
            emit(RecognitionEvent.End)
            activeEmitter.set(null)
        }

        override fun onPartialResults(partialResults: Bundle) {
            val matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull().orEmpty()
            YasnaSpeechLog.dRecognized("onPartialResults count=${matches?.size ?: 0}", text)
            emit(RecognitionEvent.Partial(text, 0f))
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun listen(locale: Locale): Flow<RecognitionEvent> = callbackFlow {
        val available = isAvailable()
        YasnaSpeechLog.d("SpeechRecognizer.isRecognitionAvailable=$available")
        if (!available) {
            trySend(RecognitionEvent.Error(-1, "Служба распознавания недоступна"))
            close()
            return@callbackFlow
        }

        val languageTag = locale.toLanguageTag().ifBlank { "ru-RU" }
        val intent = buildRecognizerIntent(languageTag)

        activeEmitter.set { event -> trySend(event) }

        mainHandler.post {
            try {
                cancelInternal()
                val instance = getOrCreateRecognizer()
                instance.setRecognitionListener(recognitionListener)
                YasnaSpeechLog.d("startListening called language=$languageTag")
                instance.startListening(intent)
            } catch (t: Throwable) {
                YasnaSpeechLog.w("startListening failed", t)
                trySend(RecognitionEvent.Error(SpeechRecognizer.ERROR_CLIENT, mapError(SpeechRecognizer.ERROR_CLIENT)))
                trySend(RecognitionEvent.End)
                activeEmitter.set(null)
                close()
            }
        }

        awaitClose {
            mainHandler.post {
                YasnaSpeechLog.d("cancel (flow closed)")
                recognizer?.cancel()
                activeEmitter.set(null)
            }
        }
    }.flowOn(Dispatchers.Main.immediate)

    fun destroy() {
        mainHandler.post {
            YasnaSpeechLog.d("destroy")
            recognizer?.destroy()
            recognizer = null
            activeEmitter.set(null)
        }
    }

    private fun getOrCreateRecognizer(): SpeechRecognizer {
        recognizer?.let { return it }
        YasnaSpeechLog.d("SpeechRecognizer created")
        val instance = SpeechRecognizer.createSpeechRecognizer(appContext)
            ?: error("SpeechRecognizer.createSpeechRecognizer returned null")
        recognizer = instance
        return instance
    }

    private fun cancelInternal() {
        YasnaSpeechLog.d("cancel (before new session)")
        recognizer?.cancel()
    }

    private fun emit(event: RecognitionEvent) {
        activeEmitter.get()?.invoke(event)
    }

    private fun buildRecognizerIntent(languageTag: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

    private fun mapError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Сеть: таймаут"
        SpeechRecognizer.ERROR_NETWORK -> "Проблема сети"
        SpeechRecognizer.ERROR_AUDIO -> "Проблема аудио"
        SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера"
        SpeechRecognizer.ERROR_CLIENT -> "Клиентская ошибка"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Нет речи"
        SpeechRecognizer.ERROR_NO_MATCH -> "Не удалось распознать"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешения на микрофон"
        else -> YasnaSpeechLog.decodeError(code)
    }
}
