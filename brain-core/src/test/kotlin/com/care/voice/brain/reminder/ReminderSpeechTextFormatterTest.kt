package com.care.voice.brain.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSpeechTextFormatterTest {

    @Test
    fun formatsWithUserNameAndReminderPrefix() {
        val result = ReminderSpeechTextFormatter.format("Таблетка")
        assertEquals("Владимир. Напоминаю: Таблетка.", result)
    }

    @Test
    fun normalizesWhitespaceAndTrailingPunctuation() {
        val result = ReminderSpeechTextFormatter.format("  таблетка..\n\nутром  ")
        assertEquals("Владимир. Напоминаю: таблетка.. утром.", result)
    }

    @Test
    fun truncatesLongContent() {
        val longText = "а".repeat(300)
        val result = ReminderSpeechTextFormatter.format(longText)
        assertTrue(result.contains("…"))
        assertTrue(result.length < "Владимир. Напоминаю: $longText.".length)
    }

    @Test
    fun blankContentReturnsEmpty() {
        assertEquals("", ReminderSpeechTextFormatter.format("   "))
    }
}
