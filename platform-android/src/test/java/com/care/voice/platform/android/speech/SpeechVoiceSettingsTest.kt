package com.care.voice.platform.android.speech

import com.care.voice.brain.speech.SpeechSettings
import com.care.voice.brain.speech.SpeechTuning
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechVoiceSettingsTest {
    @Test
    fun resolvedPiperSpeedUsesDefaultSlowPreset() {
        assertEquals(1.12f, SpeechSettings().resolvedPiperSpeed(), 0.001f)
    }

    @Test
    fun piperSpeedIsClampedToSupportedRange() {
        assertEquals(SpeechTuning.PIPER_SPEED_MAX, SpeechSettings(piperSpeed = 9f).resolvedPiperSpeed(), 0.001f)
        assertEquals(SpeechTuning.PIPER_SPEED_MIN, SpeechSettings(piperSpeed = 0.1f).resolvedPiperSpeed(), 0.001f)
    }

    @Test
    fun defaultPreferredVoiceIsIrina() {
        assertEquals("ru_RU-irina-medium", SpeechSettings().preferredVoiceId)
    }
}
