package com.care.voice.ui.speak

import com.care.voice.platform.voice.RecognitionErrorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStateMachineTest {

    @Test
    fun idleMicStartsListening() {
        val t = VoiceStateMachine.transition(VoiceState.Idle, VoiceEvent.MicPressed)
        assertEquals(VoiceState.StartingListening, t?.to)
        assertEquals("mic_start", t?.reason)
    }

    @Test
    fun listeningMicCancelsToIdle() {
        val t = VoiceStateMachine.transition(VoiceState.Listening, VoiceEvent.MicPressed)
        assertEquals(VoiceState.Idle, t?.to)
        assertEquals("mic_cancel_listening", t?.reason)
    }

    @Test
    fun speakingMicStopsToIdle() {
        val t = VoiceStateMachine.transition(VoiceState.Speaking, VoiceEvent.MicPressed)
        assertEquals(VoiceState.Idle, t?.to)
        assertEquals("mic_stop_speaking", t?.reason)
    }

    @Test
    fun processingMicCancelsToIdle() {
        val t = VoiceStateMachine.transition(VoiceState.Processing, VoiceEvent.MicPressed)
        assertEquals(VoiceState.Idle, t?.to)
        assertEquals("mic_cancel_processing", t?.reason)
    }

    @Test
    fun followUpMicStartsListening() {
        val t = VoiceStateMachine.transition(VoiceState.FollowUpWindow, VoiceEvent.MicPressed)
        assertEquals(VoiceState.StartingListening, t?.to)
        assertEquals("mic_follow_up", t?.reason)
    }

    @Test
    fun recognitionFinalMovesToProcessing() {
        val t = VoiceStateMachine.transition(
            VoiceState.Listening,
            VoiceEvent.RecognitionFinal("привет")
        )
        assertEquals(VoiceState.Processing, t?.to)
    }

    @Test
    fun recognitionFinalFromFollowUpMovesToProcessing() {
        val t = VoiceStateMachine.transition(
            VoiceState.FollowUpWindow,
            VoiceEvent.RecognitionFinal("продолжаю")
        )
        assertEquals(VoiceState.Processing, t?.to)
    }

    @Test
    fun ttsStartedMovesToSpeaking() {
        val t = VoiceStateMachine.transition(VoiceState.Processing, VoiceEvent.TtsStarted)
        assertEquals(VoiceState.Speaking, t?.to)
    }

    @Test
    fun ttsDoneMovesToFollowUpWindow() {
        val t = VoiceStateMachine.transition(VoiceState.Speaking, VoiceEvent.TtsDone)
        assertEquals(VoiceState.FollowUpWindow, t?.to)
    }

    @Test
    fun ttsDoneFromProcessingMovesToFollowUpWindow() {
        val t = VoiceStateMachine.transition(VoiceState.Processing, VoiceEvent.TtsDone)
        assertEquals(VoiceState.FollowUpWindow, t?.to)
    }

    @Test
    fun followUpTimeoutFromListeningGoesIdle() {
        val t = VoiceStateMachine.transition(VoiceState.Listening, VoiceEvent.FollowUpTimeout)
        assertEquals(VoiceState.Idle, t?.to)
    }

    @Test
    fun softFailureMovesToIdle() {
        val t = VoiceStateMachine.transition(
            VoiceState.Listening,
            VoiceEvent.RecognitionSoftFailure(RecognitionErrorKind.NoSpeech)
        )
        assertEquals(VoiceState.Idle, t?.to)
    }

    @Test
    fun technicalFailureMovesToError() {
        val t = VoiceStateMachine.transition(
            VoiceState.Listening,
            VoiceEvent.RecognitionTechnicalError("Проблема сети")
        )
        assertEquals(VoiceState.Error, t?.to)
    }

    @Test
    fun assistantFailedMovesToError() {
        val t = VoiceStateMachine.transition(
            VoiceState.Processing,
            VoiceEvent.AssistantFailed("ошибка")
        )
        assertEquals(VoiceState.Error, t?.to)
    }

    @Test
    fun ttsErrorMovesToFollowUpWindowFromSpeaking() {
        val t = VoiceStateMachine.transition(VoiceState.Speaking, VoiceEvent.TtsError)
        assertEquals(VoiceState.FollowUpWindow, t?.to)
    }

    @Test
    fun ttsErrorMovesToFollowUpWindowFromProcessing() {
        val t = VoiceStateMachine.transition(VoiceState.Processing, VoiceEvent.TtsError)
        assertEquals(VoiceState.FollowUpWindow, t?.to)
    }

    @Test
    fun speechRecognizerNotAllowedDuringSpeaking() {
        assertTrue(!VoiceStateMachine.allowsSpeechRecognizer(VoiceState.Speaking))
        assertTrue(!VoiceStateMachine.allowsSpeechRecognizer(VoiceState.Processing))
    }

    @Test
    fun speechRecognizerAllowedDuringListening() {
        assertTrue(VoiceStateMachine.allowsSpeechRecognizer(VoiceState.Listening))
        assertTrue(VoiceStateMachine.allowsSpeechRecognizer(VoiceState.FollowUpWindow))
    }
}
