package com.care.voice.ui.speak

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.care.voice.brain.AssistantInput
import com.care.voice.brain.AssistantResult
import com.care.voice.brain.util.TextSanitizer
import com.care.voice.core.ServiceLocator
import com.care.voice.platform.voice.RecognitionEvent
import com.care.voice.platform.voice.YasnaSpeechLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

private val RU_LOCALE: Locale = Locale.forLanguageTag("ru-RU")

data class SpeakUiState(
    val listening: Boolean = false,
    val finalText: String = "",
    val assistantText: String = "",
    val error: String? = null,
    val rms: Float = 0f,
    val speaking: Boolean = false,
    val autoContinue: Boolean = true
)

class SpeakViewModel : ViewModel() {
    private val voice = ServiceLocator.recognition
    private val tts = ServiceLocator.tts
    private val orchestrator = ServiceLocator.assistantOrchestrator

    var state = androidx.compose.runtime.mutableStateOf(SpeakUiState())
        private set

    private var listenJob: Job? = null
    private var assistantJob: Job? = null
    private var speakToken = 0

    fun toggle(locale: Locale = RU_LOCALE) {
        if (state.value.listening) stop() else start(locale)
    }

    private fun start(locale: Locale) {
        val permissionGranted = ContextCompat.checkSelfPermission(
            ServiceLocator.app,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        YasnaSpeechLog.d("ViewModel start listening requested locale=${locale.toLanguageTag()}")
        YasnaSpeechLog.d("RECORD_AUDIO permission state=$permissionGranted")
        YasnaSpeechLog.d("SpeechRecognizer.isRecognitionAvailable=${voice.isAvailable()}")

        if (!permissionGranted) {
            YasnaSpeechLog.w("start aborted: RECORD_AUDIO not granted")
            state.value = state.value.copy(
                listening = false,
                error = "Нет разрешения на микрофон"
            )
            return
        }

        if (state.value.speaking) {
            YasnaSpeechLog.d("stopListening (TTS was active)")
            tts.stop()
            speakToken++
            state.value = state.value.copy(speaking = false)
        }
        listenJob?.cancel()
        listenJob = null
        state.value = state.value.copy(listening = true, error = null)
        listenJob = viewModelScope.launch {
            voice.listen(locale).collect { ev ->
                when (ev) {
                    is RecognitionEvent.Ready -> state.value = state.value.copy(rms = 0f)
                    is RecognitionEvent.Rms -> state.value = state.value.copy(rms = ev.value)
                    is RecognitionEvent.Partial -> Unit
                    is RecognitionEvent.Final -> {
                        val userUi = TextSanitizer.forUi(ev.text)
                        state.value = state.value.copy(
                            finalText = userUi,
                            listening = false
                        )
                        handleAssistant(ev.text)
                    }
                    is RecognitionEvent.Error -> {
                        state.value = state.value.copy(error = ev.message, listening = false)
                        maybeAutoListen()
                    }
                    is RecognitionEvent.End -> state.value = state.value.copy(listening = false)
                }
            }
        }
    }

    private fun stop() {
        YasnaSpeechLog.d("stopListening (ViewModel)")
        listenJob?.cancel()
        listenJob = null
        state.value = state.value.copy(listening = false)
    }

    private fun handleAssistant(userText: String) {
        assistantJob?.cancel()
        assistantJob = viewModelScope.launch {
            state.value = state.value.copy(error = null)

            when (val result = orchestrator.handle(AssistantInput.UserMessage(userText.trim()))) {
                is AssistantResult.Reply -> deliverAssistantSpeech(result.text)
                is AssistantResult.ConfirmationRequired -> deliverAssistantSpeech(result.text)
                is AssistantResult.ActionCompleted -> deliverAssistantSpeech(result.text)
                is AssistantResult.ActionCancelled -> deliverAssistantSpeech(result.text)
                is AssistantResult.Failure -> {
                    speakToken++
                    tts.stop()
                    state.value = state.value.copy(
                        error = result.userMessage,
                        speaking = false
                    )
                    maybeAutoListen()
                }
            }
        }
    }

    private fun deliverAssistantSpeech(rawAnswer: String) {
        val uiText = TextSanitizer.forUi(rawAnswer)
        val ttsText = TextSanitizer.forTts(rawAnswer)

        state.value = state.value.copy(
            assistantText = uiText,
            error = null
        )

        tts.stop()
        val myToken = ++speakToken
        state.value = state.value.copy(speaking = true)

        tts.speak(ttsText) {
            if (myToken != speakToken) return@speak
            state.value = state.value.copy(speaking = false)
            maybeAutoListen()
        }
    }

    private fun maybeAutoListen() {
        if (state.value.autoContinue && !state.value.listening && !state.value.speaking) {
            start(RU_LOCALE)
        }
    }

    fun repeatAssistant() {
        val ui = state.value.assistantText
        if (ui.isBlank()) return
        val ttsText = TextSanitizer.forTts(ui)
        tts.stop()
        val myToken = ++speakToken
        state.value = state.value.copy(speaking = true)
        tts.speak(ttsText) {
            if (myToken != speakToken) return@speak
            state.value = state.value.copy(speaking = false)
        }
    }

    fun stopSpeaking() {
        speakToken++
        tts.stop()
        state.value = state.value.copy(speaking = false)
    }

    override fun onCleared() {
        assistantJob?.cancel()
        listenJob?.cancel()
        listenJob = null
        voice.destroy()
        tts.shutdown()
        super.onCleared()
    }
}
