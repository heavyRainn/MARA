package com.care.voice.ui.speak

import com.care.voice.brain.vision.VisionResponseSanitizer
import com.care.voice.brain.vision.VisionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Documents that only sanitized vision text reaches TTS / history pipelines.
 */
class VisionSpeechPathTest {

    @Test
    fun successfulVisionResultProvidesNonBlankTextForTts() {
        val result = VisionResult.Success("На фото белая компьютерная мышь.")
        assertTrue(result.text.isNotBlank())
    }

    @Test
    fun reasoningNeverReachesTtsPipeline() {
        val raw = """
            <think>English internal reasoning...</think>
            На фотографии компьютерная мышь.
        """.trimIndent()
        val forTts = VisionResponseSanitizer.sanitize(raw)
        assertEquals("На фотографии компьютерная мышь.", forTts)
        assertFalse(forTts.contains("English"))
        assertFalse(forTts.contains("think", ignoreCase = true))
    }

    @Test
    fun reasoningNeverSavedToHistory() {
        val historyLine = VisionResponseSanitizer.sanitize(
            content = "Итог.",
            reasoningField = "must not be stored",
        )
        assertEquals("Итог.", historyLine)
        assertFalse(historyLine.contains("must not"))
    }
}
