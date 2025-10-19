package com.care.voice.ui.speak

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.care.voice.core.ServiceLocator
import com.care.voice.core.TextSanitizer
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
    val speaking: Boolean = false,
    val autoContinue: Boolean = true        // ★ флаг автоповтора прослушки
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
                    is RecognitionEvent.Partial -> state.value =
                        state.value.copy(
                            // ★ можно мягко чистить промежуточный текст
                            partial = TextSanitizer.forUi(ev.text).take(200)
                        )
                    is RecognitionEvent.Final -> {
                        // ★ показываем пользователю очищенную версию
                        val userUi = TextSanitizer.forUi(ev.text)
                        state.value = state.value.copy(
                            finalText = userUi,
                            listening = false,
                            partial = ""
                        )
                        // В LLM отправляем сырой (или trimmed) текст
                        handleAssistant(ev.text)
                    }
                    is RecognitionEvent.Error -> state.value = state.value.copy(error = ev.message, listening = false)
                    is RecognitionEvent.End -> state.value = state.value.copy(listening = false)
                }
            }
        }
    }

    private fun stop() { listenJob?.cancel(); state.value = state.value.copy(listening = false) }

    // чтобы не путались колбэки разных озвучек
    private var speakToken = 0

    private fun handleAssistant(userText: String) = viewModelScope.launch {
        state.value = state.value.copy(error = null)

        val result = repo.chat(userText.trim())
        result.onSuccess { rawAnswer ->
            // 1) очищаем двумя профилями
            val uiText  = TextSanitizer.forUi(rawAnswer)
            val ttsText = TextSanitizer.forTts(rawAnswer)

            // 2) кладём на экран именно Ui-версию
            state.value = state.value.copy(assistantText = uiText)

            // 3) безопасный перезапуск TTS
            tts.stop()
            val myToken = ++speakToken
            state.value = state.value.copy(speaking = true)

            // 4) озвучиваем TTS-версию
            tts.speak(ttsText) {
                if (myToken != speakToken) return@speak
                state.value = state.value.copy(speaking = false)

                // 5) автопетля — по флагу
                if (state.value.autoContinue) {
                    start(Locale("ru","RU"))
                }
            }
        }.onFailure { e ->
            state.value = state.value.copy(
                error = "Ошибка ИИ: ${e.message}",
                speaking = false
            )
        }
    }

    fun repeatAssistant() {
        val ui = state.value.assistantText
        if (ui.isBlank()) return
        // ★ Повторяем через жёсткую очистку, чтобы не читались лишние символы
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
        tts.stop()
        state.value = state.value.copy(speaking = false)
    }

    fun debugAskLLM(text: String) {
        // ★ показываем пользователю очищенную версию
        val userUi = TextSanitizer.forUi(text)
        state.value = state.value.copy(
            finalText = userUi,
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
