package com.care.voice.ui.speak

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.care.voice.brain.AssistantInput
import com.care.voice.brain.AssistantResult
import com.care.voice.brain.ReminderSetupKind
import com.care.voice.brain.util.TextSanitizer
import com.care.voice.core.ServiceLocator
import com.care.voice.platform.tts.TtsCallbacks
import com.care.voice.platform.voice.RecognitionErrorKind
import com.care.voice.platform.voice.RecognitionEvent
import com.care.voice.platform.voice.YasnaSpeechLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.Locale

private val RU_LOCALE: Locale = Locale.forLanguageTag("ru-RU")
private const val LISTENING_START_TIMEOUT_MS = 8_000L
private const val TRANSIENT_HINT_MS = 3_000L

data class SpeakUiState(
    val voiceState: VoiceState = VoiceState.Idle,
    val finalText: String = "",
    val assistantText: String = "",
    val error: String? = null,
    val transientHint: String? = null,
    val rms: Float = 0f,
    val sessionToken: Long = 0L
)

class SpeakViewModel : ViewModel() {
    private val voice = ServiceLocator.recognition
    private val tts = ServiceLocator.tts
    private val orchestrator = ServiceLocator.assistantOrchestrator

    var state = androidx.compose.runtime.mutableStateOf(SpeakUiState())
        private set

    private var listenJob: Job? = null
    private var assistantJob: Job? = null
    private var followUpJob: Job? = null
    private var listenStartTimeoutJob: Job? = null
    private var transientHintJob: Job? = null
    private var sessionToken = 0L
    private var activeListenToken = 0L
    private var finalHandledForToken = 0L
    private var ttsUtteranceId: String? = null
    private var followUpActive = false
    private var activeListenMode = ListenMode.Manual

    private val _reminderSetupRequests = MutableSharedFlow<ReminderSetupRequest>(extraBufferCapacity = 1)
    val reminderSetupRequests = _reminderSetupRequests.asSharedFlow()
    private var pendingExactAlarmRetryId: String? = null
    var pendingNotificationRetryId: String? = null
        private set

    data class ReminderSetupRequest(
        val kind: ReminderSetupKind,
        val pendingActionId: String
    )

    fun setPendingNotificationRetry(pendingActionId: String) {
        pendingNotificationRetryId = pendingActionId
    }

    fun clearPendingNotificationRetry() {
        pendingNotificationRetryId = null
    }

    fun onActivityResumed() {
        val pendingId = pendingExactAlarmRetryId ?: return
        if (!ServiceLocator.reminderCapabilityChecker.canScheduleExactAlarms()) return
        pendingExactAlarmRetryId = null
        retryPendingReminder(pendingId)
    }

    fun retryPendingReminder(pendingActionId: String) {
        assistantJob?.cancel()
        assistantJob = viewModelScope.launch {
            processAssistantResult(orchestrator.retryPendingReminderSchedule(pendingActionId))
        }
    }

    fun onMicPressed(locale: Locale = RU_LOCALE) {
        val current = state.value.voiceState
        val transition = VoiceStateMachine.transition(current, VoiceEvent.MicPressed) ?: run {
            YasnaSpeechLog.d("VoiceFSM ignored mic in $current sessionToken=$sessionToken")
            return
        }

        when (transition.reason) {
            "mic_start", "mic_follow_up" -> {
                if (transition.reason == "mic_follow_up") endFollowUpWindow()
                beginListening(locale, transition.reason)
            }
            "mic_cancel_listening" -> {
                applyTransition(transition)
                cancelListening(transition.reason)
            }
            "mic_stop_speaking" -> {
                applyTransition(transition)
                stopSpeaking(transition.reason)
            }
        }
    }

    fun toggle(locale: Locale = RU_LOCALE) = onMicPressed(locale)

    fun repeatAssistant() {
        val ui = state.value.assistantText
        if (ui.isBlank()) return
        if (state.value.voiceState == VoiceState.Processing ||
            state.value.voiceState == VoiceState.Speaking
        ) return

        ensureRecognizerStopped("repeat_assistant")
        tts.stop()
        prepareForTts("repeat_assistant")
        speakTts(TextSanitizer.forTts(ui), "repeat_assistant")
    }

    fun stopSpeaking() = stopSpeaking("ui_stop")

    private fun beginListening(locale: Locale, reason: String) {
        when (state.value.voiceState) {
            VoiceState.Processing, VoiceState.Speaking -> return
            VoiceState.Listening -> return
            VoiceState.FollowUpWindow -> applyTransition(
                VoiceTransition(VoiceState.FollowUpWindow, VoiceState.StartingListening, reason)
            )
            VoiceState.Idle, VoiceState.Error, VoiceState.StartingListening -> applyTransition(
                VoiceTransition(state.value.voiceState, VoiceState.StartingListening, reason)
            )
        }

        activeListenMode = when (reason) {
            "follow_up_auto" -> ListenMode.FollowUpAuto
            else -> ListenMode.Manual
        }

        activeListenToken = ++sessionToken
        finalHandledForToken = 0L

        ensureTtsStopped("before_listening")
        cancelListenStartTimeout()
        clearTransientHint()

        val permissionGranted = ContextCompat.checkSelfPermission(
            ServiceLocator.app,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            onRecognitionFailed(RecognitionErrorKind.PermissionDenied, activeListenToken, fromStart = true)
            return
        }

        if (!voice.isAvailable()) {
            applyTransition(
                VoiceTransition(state.value.voiceState, VoiceState.Error, "recognizer_unavailable")
            )
            state.value = state.value.copy(error = "Служба распознавания недоступна")
            return
        }

        listenJob?.cancel()
        listenJob = null
        state.value = state.value.copy(
            sessionToken = activeListenToken,
            error = null,
            transientHint = null,
            rms = 0f
        )

        YasnaSpeechLog.d(
            "beginListening reason=$reason mode=$activeListenMode sessionToken=$activeListenToken"
        )

        listenStartTimeoutJob = viewModelScope.launch {
            delay(LISTENING_START_TIMEOUT_MS)
            if (state.value.voiceState == VoiceState.StartingListening &&
                activeListenToken == sessionToken
            ) {
                YasnaSpeechLog.w("listen start timeout sessionToken=$activeListenToken")
                onRecognitionFailed(RecognitionErrorKind.Unknown, activeListenToken, fromStart = true)
            }
        }

        listenJob = viewModelScope.launch {
            val tokenForJob = activeListenToken
            voice.listen(locale, tokenForJob).collect { ev ->
                if (ev is RecognitionEvent.Final && ev.sessionToken != activeListenToken) return@collect
                if (ev is RecognitionEvent.Error && ev.sessionToken != activeListenToken) return@collect

                when (ev) {
                    is RecognitionEvent.Ready -> {
                        cancelListenStartTimeout()
                        dispatch(VoiceEvent.RecognitionReady, "on_ready")
                    }
                    is RecognitionEvent.Rms -> {
                        if (state.value.voiceState == VoiceState.StartingListening) {
                            cancelListenStartTimeout()
                            dispatch(VoiceEvent.RecognitionReady, "on_rms")
                        }
                        state.value = state.value.copy(rms = ev.value)
                    }
                    is RecognitionEvent.Partial -> Unit
                    is RecognitionEvent.Final -> onFinalResult(ev.text, ev.sessionToken)
                    is RecognitionEvent.Error -> onRecognitionFailed(ev.kind, ev.sessionToken)
                    is RecognitionEvent.End -> Unit
                }
            }
        }
    }

    private fun onFinalResult(text: String, token: Long) {
        if (token != activeListenToken) {
            YasnaSpeechLog.w("final ignored stale token=$token active=$activeListenToken")
            return
        }
        if (finalHandledForToken == token) {
            YasnaSpeechLog.w("final ignored duplicate token=$token")
            return
        }
        finalHandledForToken = token

        endFollowUpWindow()
        voice.cancelActiveSession("final_received")
        listenJob?.cancel()
        listenJob = null
        clearTransientHint()

        val userUi = TextSanitizer.forUi(text)
        dispatch(VoiceEvent.RecognitionFinal(text), "final_result")
        state.value = state.value.copy(finalText = userUi)
        handleAssistant(text)
    }

    private fun onRecognitionFailed(
        kind: RecognitionErrorKind,
        token: Long,
        fromStart: Boolean = false
    ) {
        if (!fromStart && token != activeListenToken) {
            YasnaSpeechLog.w("error ignored stale token=$token active=$activeListenToken kind=$kind")
            return
        }

        cancelListenStartTimeout()
        endFollowUpWindow()
        voice.cancelActiveSession("recognition_error")
        listenJob?.cancel()
        listenJob = null

        val fromState = state.value.voiceState
        val outcome = RecognitionOutcomeResolver.resolve(kind, activeListenMode, fromState)

        YasnaSpeechLog.d(
            "recognitionFailed kind=$kind mode=$activeListenMode from=$fromState -> ${outcome.nextState}"
        )

        if (RecognitionOutcomeResolver.isTechnical(kind)) {
            dispatch(VoiceEvent.RecognitionTechnicalError(outcome.errorBanner.orEmpty()), "recognition_technical")
            state.value = state.value.copy(
                error = outcome.errorBanner,
                transientHint = null
            )
            clearTransientHint()
        } else {
            dispatch(VoiceEvent.RecognitionSoftFailure(kind), "recognition_soft")
            state.value = state.value.copy(error = null)
            outcome.transientHint?.let { showTransientHint(it) } ?: clearTransientHint()
        }
    }

    private fun showTransientHint(hint: String) {
        transientHintJob?.cancel()
        state.value = state.value.copy(transientHint = hint, error = null)
        transientHintJob = viewModelScope.launch {
            delay(TRANSIENT_HINT_MS)
            if (state.value.transientHint == hint) {
                state.value = state.value.copy(transientHint = null)
            }
        }
    }

    private fun clearTransientHint() {
        transientHintJob?.cancel()
        transientHintJob = null
        state.value = state.value.copy(transientHint = null)
    }

    private fun cancelListening(reason: String) {
        cancelListenStartTimeout()
        clearTransientHint()
        endFollowUpWindow()
        voice.cancelActiveSession(reason)
        listenJob?.cancel()
        listenJob = null
        val current = state.value.voiceState
        if (current == VoiceState.StartingListening || current == VoiceState.Listening) {
            dispatch(VoiceEvent.RecognitionCancelled, reason)
        } else if (followUpActive) {
            followUpActive = false
            applyTransition(VoiceTransition(current, VoiceState.Idle, reason))
        }
    }

    private fun handleAssistant(userText: String) {
        assistantJob?.cancel()
        assistantJob = viewModelScope.launch {
            processAssistantResult(orchestrator.handle(AssistantInput.UserMessage(userText.trim())))
        }
    }

    private suspend fun processAssistantResult(result: AssistantResult) {
        when (result) {
            is AssistantResult.Reply -> deliverAssistantSpeech(result.text)
            is AssistantResult.ConfirmationRequired -> deliverAssistantSpeech(result.text)
            is AssistantResult.ActionCompleted -> deliverAssistantSpeech(result.text)
            is AssistantResult.ActionCancelled -> deliverAssistantSpeech(result.text)
            is AssistantResult.ReminderSetupRequired -> {
                state.value = state.value.copy(
                    assistantText = TextSanitizer.forUi(result.userMessage),
                    error = null
                )
                dispatch(VoiceEvent.AssistantReplyReady(result.userMessage), "reminder_setup_required")
                prepareForTts("reminder_setup_required")
                speakTts(TextSanitizer.forTts(result.userMessage), "reminder_setup_required")
                if (result.kind == ReminderSetupKind.EXACT_ALARM_PERMISSION) {
                    pendingExactAlarmRetryId = result.pendingActionId
                }
                _reminderSetupRequests.emit(
                    ReminderSetupRequest(result.kind, result.pendingActionId)
                )
            }
            is AssistantResult.Failure -> {
                state.value = state.value.copy(
                    assistantText = TextSanitizer.forUi(result.userMessage),
                    error = result.userMessage
                )
                dispatch(VoiceEvent.AssistantFailed(result.userMessage), "assistant_failure")
            }
        }
    }

    private fun deliverAssistantSpeech(rawAnswer: String) {
        ensureRecognizerStopped("before_tts")
        tts.stop()

        val uiText = TextSanitizer.forUi(rawAnswer)
        val ttsText = TextSanitizer.forTts(rawAnswer)
        state.value = state.value.copy(assistantText = uiText, error = null)
        dispatch(VoiceEvent.AssistantReplyReady(uiText), "assistant_reply")
        prepareForTts("assistant_reply")
        speakTts(ttsText, "assistant_reply")
    }

    private fun prepareForTts(reason: String) {
        if (state.value.voiceState != VoiceState.Processing) {
            applyTransition(
                VoiceTransition(state.value.voiceState, VoiceState.Processing, reason)
            )
        }
    }

    private fun speakTts(text: String, reason: String) {
        ensureRecognizerStopped("tts_start")
        val utteranceId = "tts-${++sessionToken}"
        ttsUtteranceId = utteranceId

        YasnaSpeechLog.d("speakTts reason=$reason utteranceId=$utteranceId sessionToken=$sessionToken")

        tts.speak(
            text = text,
            utteranceId = utteranceId,
            callbacks = TtsCallbacks(
                onStart = {
                    if (ttsUtteranceId != utteranceId) return@TtsCallbacks
                    dispatch(VoiceEvent.TtsStarted, "tts_on_start")
                },
                onDone = {
                    if (ttsUtteranceId != utteranceId) return@TtsCallbacks
                    ttsUtteranceId = null
                    dispatch(VoiceEvent.TtsDone, "tts_on_done")
                },
                onError = {
                    if (ttsUtteranceId != utteranceId) return@TtsCallbacks
                    ttsUtteranceId = null
                    val message = state.value.assistantText.ifBlank { "Не удалось озвучить ответ." }
                    state.value = state.value.copy(error = message)
                    dispatch(VoiceEvent.TtsError, "tts_on_error")
                }
            )
        )
    }

    private fun endFollowUpWindow() {
        followUpActive = false
        cancelFollowUpTimer()
    }

    private fun stopSpeaking(reason: String) {
        ttsUtteranceId = null
        tts.stop()
        endFollowUpWindow()
        ensureRecognizerStopped(reason)
        dispatch(VoiceEvent.TtsStopped, reason)
    }

    private fun ensureTtsStopped(reason: String) {
        if (ttsUtteranceId != null || state.value.voiceState == VoiceState.Speaking) {
            ttsUtteranceId = null
            tts.stop()
            YasnaSpeechLog.d("ensureTtsStopped reason=$reason sessionToken=$sessionToken")
        }
    }

    private fun ensureRecognizerStopped(reason: String) {
        if (listenJob != null ||
            state.value.voiceState == VoiceState.Listening ||
            state.value.voiceState == VoiceState.StartingListening
        ) {
            voice.cancelActiveSession(reason)
            listenJob?.cancel()
            listenJob = null
            YasnaSpeechLog.d("ensureRecognizerStopped reason=$reason sessionToken=$sessionToken")
        }
    }

    private fun cancelFollowUpTimer() {
        followUpJob?.cancel()
        followUpJob = null
    }

    private fun cancelListenStartTimeout() {
        listenStartTimeoutJob?.cancel()
        listenStartTimeoutJob = null
    }

    private fun dispatch(event: VoiceEvent, reason: String) {
        val current = state.value.voiceState
        val transition = VoiceStateMachine.transition(current, event) ?: run {
            YasnaSpeechLog.d("VoiceFSM ignored $event in $current reason=$reason sessionToken=$sessionToken")
            return
        }
        applyTransition(transition)
    }

    private fun applyTransition(transition: VoiceTransition) {
        YasnaSpeechLog.d(
            "VoiceFSM ${transition.from} -> ${transition.to} reason=${transition.reason} sessionToken=$sessionToken"
        )
        state.value = state.value.copy(voiceState = transition.to)
    }

    override fun onCleared() {
        cancelListenStartTimeout()
        clearTransientHint()
        endFollowUpWindow()
        assistantJob?.cancel()
        listenJob?.cancel()
        listenJob = null
        voice.destroy()
        tts.shutdown()
        super.onCleared()
    }
}
