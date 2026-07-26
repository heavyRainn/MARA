package com.care.voice.ui.speak

import com.care.voice.platform.voice.RecognitionErrorKind

enum class VoiceState {
    Idle,
    StartingListening,
    Listening,
    Processing,
    Speaking,
    FollowUpWindow,
    Error
}

sealed interface VoiceEvent {
    data object MicPressed : VoiceEvent
    data object PermissionDenied : VoiceEvent
    data object RecognitionReady : VoiceEvent
    data class RecognitionFinal(val text: String) : VoiceEvent
    data class RecognitionSoftFailure(val kind: RecognitionErrorKind) : VoiceEvent
    data class RecognitionTechnicalError(val message: String) : VoiceEvent
    data object RecognitionCancelled : VoiceEvent
    data object AssistantProcessingStarted : VoiceEvent
    data class AssistantFailed(val message: String) : VoiceEvent
    data class AssistantReplyReady(val text: String) : VoiceEvent
    data object TtsStarted : VoiceEvent
    data object TtsDone : VoiceEvent
    data object TtsError : VoiceEvent
    data object TtsStopped : VoiceEvent
    data object FollowUpTimeout : VoiceEvent
}

data class VoiceTransition(
    val from: VoiceState,
    val to: VoiceState,
    val reason: String
)

object VoiceStateMachine {

    fun transition(state: VoiceState, event: VoiceEvent): VoiceTransition? = when (event) {
        VoiceEvent.MicPressed -> micTransition(state)
        VoiceEvent.PermissionDenied -> if (state == VoiceState.StartingListening) {
            VoiceTransition(state, VoiceState.Error, "permission_denied")
        } else null

        VoiceEvent.RecognitionReady -> when (state) {
            VoiceState.StartingListening ->
                VoiceTransition(state, VoiceState.Listening, "recognition_ready")
            else -> null
        }

        is VoiceEvent.RecognitionFinal -> when (state) {
            VoiceState.Listening, VoiceState.FollowUpWindow ->
                VoiceTransition(state, VoiceState.Processing, "recognition_final")
            else -> null
        }

        is VoiceEvent.RecognitionSoftFailure -> when (state) {
            VoiceState.StartingListening, VoiceState.Listening, VoiceState.FollowUpWindow ->
                VoiceTransition(state, VoiceState.Idle, "recognition_soft_${event.kind.name.lowercase()}")
            else -> null
        }

        is VoiceEvent.RecognitionTechnicalError -> when (state) {
            VoiceState.StartingListening, VoiceState.Listening, VoiceState.FollowUpWindow ->
                VoiceTransition(state, VoiceState.Error, "recognition_technical")
            else -> null
        }

        VoiceEvent.RecognitionCancelled -> when (state) {
            VoiceState.StartingListening, VoiceState.Listening, VoiceState.FollowUpWindow ->
                VoiceTransition(state, VoiceState.Idle, "recognition_cancelled")
            else -> null
        }

        VoiceEvent.AssistantProcessingStarted -> when (state) {
            VoiceState.Processing -> null
            else -> null
        }

        is VoiceEvent.AssistantFailed -> when (state) {
            VoiceState.Processing ->
                VoiceTransition(state, VoiceState.Error, "assistant_failed")
            else -> null
        }

        is VoiceEvent.AssistantReplyReady -> when (state) {
            VoiceState.Processing -> null
            else -> null
        }

        VoiceEvent.TtsStarted -> when (state) {
            VoiceState.Processing ->
                VoiceTransition(state, VoiceState.Speaking, "tts_started")
            else -> null
        }

        VoiceEvent.TtsDone -> when (state) {
            VoiceState.Speaking ->
                VoiceTransition(state, VoiceState.Idle, "tts_done")
            else -> null
        }

        VoiceEvent.TtsError -> when (state) {
            VoiceState.Processing, VoiceState.Speaking ->
                VoiceTransition(state, VoiceState.Error, "tts_error")
            else -> null
        }

        VoiceEvent.TtsStopped -> when (state) {
            VoiceState.Speaking ->
                VoiceTransition(state, VoiceState.Idle, "tts_stopped")
            else -> null
        }

        VoiceEvent.FollowUpTimeout -> when (state) {
            VoiceState.FollowUpWindow, VoiceState.StartingListening, VoiceState.Listening ->
                VoiceTransition(state, VoiceState.Idle, "follow_up_timeout")
            else -> null
        }
    }

    private fun micTransition(state: VoiceState): VoiceTransition? = when (state) {
        VoiceState.Idle, VoiceState.Error ->
            VoiceTransition(state, VoiceState.StartingListening, "mic_start")
        VoiceState.Listening, VoiceState.StartingListening ->
            VoiceTransition(state, VoiceState.Idle, "mic_cancel_listening")
        VoiceState.Speaking ->
            VoiceTransition(state, VoiceState.Idle, "mic_stop_speaking")
        VoiceState.Processing -> null
        VoiceState.FollowUpWindow ->
            VoiceTransition(state, VoiceState.StartingListening, "mic_follow_up")
    }

    fun allowsSpeechRecognizer(state: VoiceState): Boolean =
        state == VoiceState.StartingListening ||
            state == VoiceState.Listening ||
            state == VoiceState.FollowUpWindow

    fun allowsTts(state: VoiceState): Boolean =
        state == VoiceState.Processing || state == VoiceState.Speaking
}
