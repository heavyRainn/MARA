package com.care.voice.brain.speech

object SpeechFailurePolicy {
    fun shouldFallback(code: SpeechFailureCode): Boolean = when (code) {
        SpeechFailureCode.PIPER_NATIVE_LIBRARY_UNAVAILABLE,
        SpeechFailureCode.PIPER_NATIVE_INITIALIZATION_FAILED,
        SpeechFailureCode.PIPER_MODEL_NOT_FOUND,
        SpeechFailureCode.PIPER_MODEL_INSTALL_FAILED,
        SpeechFailureCode.PIPER_MODEL_CHECKSUM_FAILED,
        SpeechFailureCode.PIPER_MODEL_CONFIG_INVALID,
        SpeechFailureCode.PIPER_MODEL_LOAD_FAILED,
        SpeechFailureCode.PIPER_PHONEMIZATION_FAILED,
        SpeechFailureCode.PIPER_INFERENCE_FAILED,
        SpeechFailureCode.PIPER_INVALID_AUDIO,
        SpeechFailureCode.PIPER_TIMEOUT,
        SpeechFailureCode.AUDIO_TRACK_INIT_FAILED,
        SpeechFailureCode.AUDIO_PLAYBACK_FAILED,
        -> true

        else -> false
    }

    fun shouldSkipFallback(reason: SpeechCancelReason): Boolean = when (reason) {
        SpeechCancelReason.USER_STARTED_LISTENING,
        SpeechCancelReason.USER_STOPPED_PLAYBACK,
        SpeechCancelReason.PHONE_CALL,
        SpeechCancelReason.REQUEST_REPLACED,
        SpeechCancelReason.OWNER_DESTROYED,
        -> true

        else -> false
    }
}
