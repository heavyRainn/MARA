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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

sealed interface RecognitionEvent {
    data class Partial(val text: String, val rms: Float) : RecognitionEvent
    data class Final(val text: String, val sessionToken: Long) : RecognitionEvent
    data class Error(
        val kind: RecognitionErrorKind,
        val rawCode: Int,
        val sessionToken: Long
    ) : RecognitionEvent
    data class Rms(val value: Float) : RecognitionEvent
    data object Ready : RecognitionEvent
    data object End : RecognitionEvent
}

private data class ActiveSession(
    val token: Long,
    val finalDelivered: AtomicBoolean,
    val emit: (RecognitionEvent) -> Unit
)

class RecognitionManager(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var recognizer: SpeechRecognizer? = null

    private val activeSession = AtomicReference<ActiveSession?>(null)
    private var rmsLogCounter = 0

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            YasnaSpeechLog.d("onReadyForSpeech sessionToken=${activeSession.get()?.token}")
            emitForActive { it.emit(RecognitionEvent.Ready) }
        }

        override fun onBeginningOfSpeech() {
            YasnaSpeechLog.d("onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (rmsLogCounter++ % 15 == 0) {
                YasnaSpeechLog.d("onRmsChanged rms=${"%.1f".format(rmsdB)}")
            }
            emitForActive { it.emit(RecognitionEvent.Rms(rmsdB)) }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            YasnaSpeechLog.d("onEndOfSpeech")
        }

        override fun onError(error: Int) {
            val session = activeSession.get()
            YasnaSpeechLog.w("onError ${YasnaSpeechLog.decodeError(error)} sessionToken=${session?.token}")
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                mainHandler.post {
                    YasnaSpeechLog.d("cancel (ERROR_RECOGNIZER_BUSY recovery)")
                    recognizer?.cancel()
                }
            }
            session?.let {
                val kind = RecognitionErrors.normalize(error)
                it.emit(RecognitionEvent.Error(kind, error, it.token))
                it.emit(RecognitionEvent.End)
            }
            clearSessionIfMatches(session?.token)
        }

        override fun onResults(results: Bundle) {
            val session = activeSession.get() ?: return
            val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull().orEmpty()
            YasnaSpeechLog.dRecognized("onResults count=${matches?.size ?: 0} sessionToken=${session.token}", text)

            if (text.isNotBlank() && session.finalDelivered.compareAndSet(false, true)) {
                session.emit(RecognitionEvent.Final(text, session.token))
                mainHandler.post {
                    YasnaSpeechLog.d("cancel after final sessionToken=${session.token}")
                    recognizer?.cancel()
                }
            } else if (text.isNotBlank()) {
                YasnaSpeechLog.w("duplicate final ignored sessionToken=${session.token}")
            }

            session.emit(RecognitionEvent.End)
            clearSessionIfMatches(session.token)
        }

        override fun onPartialResults(partialResults: Bundle) {
            val matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull().orEmpty()
            YasnaSpeechLog.dRecognized("onPartialResults count=${matches?.size ?: 0}", text)
            emitForActive { it.emit(RecognitionEvent.Partial(text, 0f)) }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun listen(locale: Locale, sessionToken: Long): Flow<RecognitionEvent> = callbackFlow {
        val available = isAvailable()
        YasnaSpeechLog.d("SpeechRecognizer.isRecognitionAvailable=$available sessionToken=$sessionToken")
        if (!available) {
            trySend(
                RecognitionEvent.Error(
                    RecognitionErrorKind.Unknown,
                    -1,
                    sessionToken
                )
            )
            close()
            return@callbackFlow
        }

        val languageTag = locale.toLanguageTag().ifBlank { "ru-RU" }
        val intent = buildRecognizerIntent(languageTag)

        val session = ActiveSession(
            token = sessionToken,
            finalDelivered = AtomicBoolean(false),
            emit = { event -> trySend(event) }
        )
        activeSession.set(session)

        mainHandler.post {
            try {
                cancelInternal()
                val instance = getOrCreateRecognizer()
                instance.setRecognitionListener(recognitionListener)
                YasnaSpeechLog.d("startListening language=$languageTag sessionToken=$sessionToken")
                instance.startListening(intent)
            } catch (t: Throwable) {
                YasnaSpeechLog.w("startListening failed sessionToken=$sessionToken", t)
                trySend(
                    RecognitionEvent.Error(
                        RecognitionErrors.normalize(SpeechRecognizer.ERROR_CLIENT),
                        SpeechRecognizer.ERROR_CLIENT,
                        sessionToken
                    )
                )
                trySend(RecognitionEvent.End)
                clearSessionIfMatches(sessionToken)
                close()
            }
        }

        awaitClose {
            mainHandler.post {
                YasnaSpeechLog.d("cancel (flow closed) sessionToken=$sessionToken")
                if (activeSession.get()?.token == sessionToken) {
                    recognizer?.cancel()
                    clearSessionIfMatches(sessionToken)
                }
            }
        }
    }.flowOn(Dispatchers.Main.immediate)

    fun cancelActiveSession(reason: String = "explicit_cancel") {
        mainHandler.post {
            val token = activeSession.get()?.token
            YasnaSpeechLog.d("cancelActiveSession reason=$reason sessionToken=$token")
            recognizer?.cancel()
            clearSessionIfMatches(token)
        }
    }

    fun destroy() {
        mainHandler.post {
            YasnaSpeechLog.d("destroy")
            recognizer?.destroy()
            recognizer = null
            activeSession.set(null)
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

    private fun emitForActive(block: (ActiveSession) -> Unit) {
        activeSession.get()?.let(block)
    }

    private fun clearSessionIfMatches(token: Long?) {
        if (token == null) return
        activeSession.updateAndGet { current ->
            if (current?.token == token) null else current
        }
    }

    private fun buildRecognizerIntent(languageTag: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
}
