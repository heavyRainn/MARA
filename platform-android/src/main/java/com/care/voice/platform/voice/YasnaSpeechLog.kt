package com.care.voice.platform.voice

import android.speech.SpeechRecognizer
import android.util.Log

object YasnaSpeechLog {
    const val TAG = "YasnaSpeech"
    private const val MAX_RELEASE_TEXT_LEN = 24

    fun d(message: String) = Log.d(TAG, message)

    fun dRecognized(prefix: String, text: String) {
        Log.d(TAG, "$prefix textLength=${text.length} preview=${safePreview(text)}")
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(TAG, message, throwable) else Log.w(TAG, message)
    }

    private fun safePreview(text: String): String {
        if (text.isEmpty()) return "<empty>"
        val trimmed = text.trim()
        return if (trimmed.length <= MAX_RELEASE_TEXT_LEN) trimmed else "${trimmed.take(MAX_RELEASE_TEXT_LEN)}…"
    }

    fun decodeError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "ERROR_NETWORK_TIMEOUT ($code) — network operation timed out"
        SpeechRecognizer.ERROR_NETWORK ->
            "ERROR_NETWORK ($code) — other network-related errors"
        SpeechRecognizer.ERROR_AUDIO ->
            "ERROR_AUDIO ($code) — audio recording error"
        SpeechRecognizer.ERROR_SERVER ->
            "ERROR_SERVER ($code) — server sends error status"
        SpeechRecognizer.ERROR_CLIENT ->
            "ERROR_CLIENT ($code) — client-side error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "ERROR_SPEECH_TIMEOUT ($code) — no speech input"
        SpeechRecognizer.ERROR_NO_MATCH ->
            "ERROR_NO_MATCH ($code) — no recognition result matched"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "ERROR_RECOGNIZER_BUSY ($code) — recognition service busy"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "ERROR_INSUFFICIENT_PERMISSIONS ($code) — insufficient permissions"
        10 -> "ERROR_TOO_MANY_REQUESTS ($code) — too many requests for the service"
        11 -> "ERROR_SERVER_DISCONNECTED ($code) — server disconnected"
        12 -> "ERROR_LANGUAGE_NOT_SUPPORTED ($code) — language not supported"
        13 -> "ERROR_LANGUAGE_UNAVAILABLE ($code) — language unavailable"
        else -> "UNKNOWN ($code)"
    }
}
