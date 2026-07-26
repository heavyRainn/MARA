package com.care.voice.platform.voice

import android.speech.SpeechRecognizer

enum class RecognitionErrorKind {
    NoSpeech,
    NoMatch,
    PermissionDenied,
    Network,
    Busy,
    Unknown
}

object RecognitionErrors {
    fun normalize(code: Int): RecognitionErrorKind = when (code) {
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> RecognitionErrorKind.NoSpeech
        SpeechRecognizer.ERROR_NO_MATCH -> RecognitionErrorKind.NoMatch
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> RecognitionErrorKind.PermissionDenied
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> RecognitionErrorKind.Network
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> RecognitionErrorKind.Busy
        SpeechRecognizer.ERROR_AUDIO,
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_CLIENT -> RecognitionErrorKind.Unknown
        else -> RecognitionErrorKind.Unknown
    }

    fun isTechnical(kind: RecognitionErrorKind): Boolean = when (kind) {
        RecognitionErrorKind.NoSpeech,
        RecognitionErrorKind.NoMatch -> false
        RecognitionErrorKind.PermissionDenied,
        RecognitionErrorKind.Network,
        RecognitionErrorKind.Busy,
        RecognitionErrorKind.Unknown -> true
    }
}
