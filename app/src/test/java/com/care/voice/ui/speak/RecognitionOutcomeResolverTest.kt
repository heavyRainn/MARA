package com.care.voice.ui.speak

import com.care.voice.platform.voice.RecognitionErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecognitionOutcomeResolverTest {

    @Test
    fun manualNoSpeechIdleWithHint() {
        val outcome = RecognitionOutcomeResolver.resolve(
            RecognitionErrorKind.NoSpeech,
            ListenMode.Manual,
            VoiceState.Listening
        )
        assertEquals(VoiceState.Idle, outcome.nextState)
        assertEquals(RecognitionOutcomeResolver.HINT_NO_SPEECH, outcome.transientHint)
        assertNull(outcome.errorBanner)
    }

    @Test
    fun followUpNoSpeechIdleWithoutHint() {
        val outcome = RecognitionOutcomeResolver.resolve(
            RecognitionErrorKind.NoSpeech,
            ListenMode.FollowUpAuto,
            VoiceState.Listening
        )
        assertEquals(VoiceState.Idle, outcome.nextState)
        assertNull(outcome.transientHint)
        assertNull(outcome.errorBanner)
    }

    @Test
    fun manualNoMatchIdleWithHint() {
        val outcome = RecognitionOutcomeResolver.resolve(
            RecognitionErrorKind.NoMatch,
            ListenMode.Manual,
            VoiceState.Listening
        )
        assertEquals(VoiceState.Idle, outcome.nextState)
        assertEquals(RecognitionOutcomeResolver.HINT_NO_MATCH, outcome.transientHint)
        assertNull(outcome.errorBanner)
    }

    @Test
    fun networkErrorIsTechnical() {
        val outcome = RecognitionOutcomeResolver.resolve(
            RecognitionErrorKind.Network,
            ListenMode.Manual,
            VoiceState.Listening
        )
        assertEquals(VoiceState.Error, outcome.nextState)
        assertNull(outcome.transientHint)
        assertEquals("Проблема сети. Проверьте подключение.", outcome.errorBanner)
    }

    @Test
    fun silenceIsNotTechnical() {
        assertEquals(false, RecognitionOutcomeResolver.isTechnical(RecognitionErrorKind.NoSpeech))
        assertEquals(false, RecognitionOutcomeResolver.isTechnical(RecognitionErrorKind.NoMatch))
    }

    @Test
    fun networkIsTechnical() {
        assertEquals(true, RecognitionOutcomeResolver.isTechnical(RecognitionErrorKind.Network))
    }
}
