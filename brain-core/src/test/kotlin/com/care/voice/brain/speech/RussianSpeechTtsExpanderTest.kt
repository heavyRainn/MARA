package com.care.voice.brain.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RussianSpeechTtsExpanderTest {

    @Test
    fun expandClockTime() {
        val result = RussianSpeechTtsExpander.expand("30 июля в 01:22")
        assertTrue(result.contains("тридцатого июля"))
        assertTrue(result.contains("один час"))
        assertTrue(result.contains("две минуты") || result.contains("двадцать две минуты"))
    }

    @Test
    fun expandClockTimeWithSpacesAroundColon() {
        val result = RussianSpeechTtsExpander.expand("напомню 5 мая в 14:05")
        assertTrue(result.contains("пятого мая"))
        assertTrue(result.contains("четырнадцать") || result.contains("14"))
    }

    @Test
    fun expandClockOnTheHour() {
        assertEquals(
            "двадцать два часа ровно",
            RussianSpeechTtsExpander.expandClock(22, 0),
        )
    }

    @Test
    fun leavesPlainTextUntouched() {
        val input = "Привет, как дела?"
        assertEquals(input, RussianSpeechTtsExpander.expand(input))
    }
}
