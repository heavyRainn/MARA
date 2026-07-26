package com.care.voice.ui.speak

import com.care.voice.platform.voice.RecognitionErrorKind
import com.care.voice.platform.voice.RecognitionErrors

enum class ListenMode {
    Manual,
    FollowUpAuto
}

data class RecognitionOutcome(
    val nextState: VoiceState,
    val transientHint: String? = null,
    val errorBanner: String? = null
)

object RecognitionOutcomeResolver {

    const val HINT_NO_SPEECH =
        "Я ничего не услышала. Нажмите и попробуйте ещё раз."
    const val HINT_NO_MATCH =
        "Не удалось разобрать слова. Попробуйте ещё раз."

    fun resolve(
        kind: RecognitionErrorKind,
        listenMode: ListenMode,
        fromState: VoiceState
    ): RecognitionOutcome = when (kind) {
        RecognitionErrorKind.NoSpeech -> when (listenMode) {
            ListenMode.FollowUpAuto -> RecognitionOutcome(nextState = VoiceState.Idle)
            ListenMode.Manual -> RecognitionOutcome(
                nextState = VoiceState.Idle,
                transientHint = HINT_NO_SPEECH
            )
        }

        RecognitionErrorKind.NoMatch -> when (listenMode) {
            ListenMode.FollowUpAuto -> RecognitionOutcome(nextState = VoiceState.Idle)
            ListenMode.Manual -> RecognitionOutcome(
                nextState = VoiceState.Idle,
                transientHint = HINT_NO_MATCH
            )
        }

        RecognitionErrorKind.PermissionDenied -> RecognitionOutcome(
            nextState = VoiceState.Error,
            errorBanner = "Нет разрешения на микрофон"
        )

        RecognitionErrorKind.Network -> RecognitionOutcome(
            nextState = VoiceState.Error,
            errorBanner = "Проблема сети. Проверьте подключение."
        )

        RecognitionErrorKind.Busy -> RecognitionOutcome(
            nextState = VoiceState.Error,
            errorBanner = "Распознаватель занят. Попробуйте через секунду."
        )

        RecognitionErrorKind.Unknown -> RecognitionOutcome(
            nextState = VoiceState.Error,
            errorBanner = technicalMessage(fromState)
        )
    }

    fun isTechnical(kind: RecognitionErrorKind): Boolean = RecognitionErrors.isTechnical(kind)

    private fun technicalMessage(fromState: VoiceState): String = when (fromState) {
        VoiceState.StartingListening -> "Микрофон не отвечает. Нажмите ещё раз."
        else -> "Не удалось распознать речь. Попробуйте ещё раз."
    }
}
