package com.care.voice.brain.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionResponseSanitizerTest {

    @Test
    fun removesClosedThinkBlockAndKeepsRussianFinal() {
        val input = """
            <think>
            English internal reasoning...
            </think>
            На фотографии компьютерная мышь.
        """.trimIndent()

        val output = VisionResponseSanitizer.sanitize(input)
        assertEquals("На фотографии компьютерная мышь.", output)
        assertFalse(output.contains("think", ignoreCase = true))
        assertFalse(output.contains("English"))
    }

    @Test
    fun dropsUnclosedThinkContent() {
        val input = """
            <think>
            The user wants me to identify the object...
        """.trimIndent()

        val output = VisionResponseSanitizer.sanitize(input)
        assertEquals("", output)
    }

    @Test
    fun ignoresSeparateReasoningField() {
        val output = VisionResponseSanitizer.sanitize(
            content = "На фото зарядка.",
            reasoningField = "Secret English reasoning that must never leak",
        )
        assertEquals("На фото зарядка.", output)
        assertFalse(output.contains("Secret"))
    }

    @Test
    fun plainRussianAnswerUnchanged() {
        val input = "На фото белая компьютерная мышь."
        assertEquals(input, VisionResponseSanitizer.sanitize(input))
    }

    @Test
    fun blankAfterSanitizeIsEmpty() {
        assertEquals("", VisionResponseSanitizer.sanitize("   "))
        assertEquals("", VisionResponseSanitizer.sanitize("<think>only reasoning</think>"))
    }

    @Test
    fun sanitizedTextIsSafeForTtsAndHistory() {
        val cleaned = VisionResponseSanitizer.sanitize(
            """
            <think>internal</think>
            Похоже, это зарядное устройство.
            """.trimIndent(),
        )
        assertTrue(cleaned.isNotBlank())
        assertFalse(cleaned.contains("<"))
        assertFalse(cleaned.contains("internal"))
    }
}
