package com.care.voice.ui.speak

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.care.voice.core.ServiceLocator
import com.care.voice.platform.voice.RecognitionEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

data class SpeakUiState(
    val listening: Boolean = false,
    val partial: String = "",
    val finalText: String = "",
    val assistantText: String = "",
    val error: String? = null,
    val rms: Float = 0f,
    val speaking: Boolean = false                // 👈 идёт озвучка
)

class SpeakViewModel : ViewModel() {
    private val voice = ServiceLocator.recognition
    private val tts = ServiceLocator.tts
    private val repo = ServiceLocator.assistantRepo

    var state = androidx.compose.runtime.mutableStateOf(SpeakUiState())
        private set

    private var listenJob: Job? = null

    fun toggle(locale: Locale = Locale("ru","RU")) {
        if (state.value.listening) stop() else start(locale)
    }

    private fun start(locale: Locale) {
        listenJob?.cancel()
        state.value = state.value.copy(listening = true, error = null, partial = "")
        listenJob = viewModelScope.launch {
            voice.listen(locale).collect { ev ->
                when (ev) {
                    is RecognitionEvent.Ready -> state.value = state.value.copy(partial = "", rms = 0f)
                    is RecognitionEvent.Rms -> state.value = state.value.copy(rms = ev.value)
                    is RecognitionEvent.Partial -> state.value = state.value.copy(partial = ev.text.take(200))
                    is RecognitionEvent.Final -> {
                        state.value = state.value.copy(
                            finalText = ev.text,   // <-- ключевая строка
                            listening = false,
                            partial = ""
                        )
                        handleAssistant(ev.text)
                    }
                    is RecognitionEvent.Error -> state.value = state.value.copy(error = ev.message, listening = false)
                    is RecognitionEvent.End -> state.value = state.value.copy(listening = false)
                }
            }
        }
    }

    private fun stop() { listenJob?.cancel(); state.value = state.value.copy(listening = false) }

    private fun handleAssistant(userText: String) = viewModelScope.launch {
        val result = repo.chat(userText)
        result.onSuccess { answer ->
            state.value = state.value.copy(assistantText = answer)
            // начинаем озвучку
            state.value = state.value.copy(speaking = true)
            tts.speak(answer) {
                // окончание озвучки
                state.value = state.value.copy(speaking = false)
                start(Locale("ru","RU")) // авто-петля при желании
            }
        }.onFailure { e ->
            state.value = state.value.copy(error = "Ошибка ИИ: ${e.message}")
        }
    }

    fun repeatAssistant() {
        val text = state.value.assistantText
        if (text.isNotBlank()) {
            state.value = state.value.copy(speaking = true)
            tts.speak(text) { state.value = state.value.copy(speaking = false) }
        }
    }

    fun stopSpeaking() {
        tts.stop()
        state.value = state.value.copy(speaking = false)
    }

    fun debugAskLLM(text: String) {
        state.value = state.value.copy(
            finalText = text,              // <-- показать последнюю фразу пользователя
            assistantText = "",
            error = null
        )
        handleAssistant(text)
    }

    override fun onCleared() {
        super.onCleared()
        tts.shutdown()
    }
}
