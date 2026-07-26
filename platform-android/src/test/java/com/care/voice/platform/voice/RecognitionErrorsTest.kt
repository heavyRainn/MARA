package com.care.voice.platform.voice

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionErrorsTest {

    @Test
    fun speechTimeoutIsNoSpeech() {
        assertEquals(
            RecognitionErrorKind.NoSpeech,
            RecognitionErrors.normalize(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        )
    }

    @Test
    fun noMatchIsNoMatch() {
        assertEquals(
            RecognitionErrorKind.NoMatch,
            RecognitionErrors.normalize(SpeechRecognizer.ERROR_NO_MATCH)
        )
    }

    @Test
    fun networkErrorsAreNetwork() {
        assertEquals(
            RecognitionErrorKind.Network,
            RecognitionErrors.normalize(SpeechRecognizer.ERROR_NETWORK)
        )
        assertEquals(
            RecognitionErrorKind.Network,
            RecognitionErrors.normalize(SpeechRecognizer.ERROR_NETWORK_TIMEOUT)
        )
    }

    @Test
    fun noSpeechAndNoMatchAreNotTechnical() {
        assertEquals(false, RecognitionErrors.isTechnical(RecognitionErrorKind.NoSpeech))
        assertEquals(false, RecognitionErrors.isTechnical(RecognitionErrorKind.NoMatch))
    }
}
