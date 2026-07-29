package com.care.voice.brain.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RussianSpeechTextNormalizerTest {
    @Test
    fun markdownHeadingBecomesSentence() {
        val result = RussianSpeechTextNormalizer.normalize("## Привет мир")
        assertTrue(result.contains("Привет мир"))
        assertTrue(!result.contains("#"))
    }

    @Test
    fun urlReplacedWithPhrase() {
        val result = RussianSpeechTextNormalizer.normalize("Смотри https://example.com/path")
        assertTrue(result.contains("ссылку"))
        assertTrue(!result.contains("https://"))
    }

    @Test
    fun codeFenceReplaced() {
        val result = RussianSpeechTextNormalizer.normalize("```kotlin\nfun main(){}\n```")
        assertTrue(result.contains("код на экран"))
    }

    @Test
    fun dosagePreserved() {
        val input = "Примите 5 мг препарата 2 раза в день"
        assertEquals(input, RussianSpeechTextNormalizer.normalize(input))
    }
}

class SpeechChunkerTest {
    @Test
    fun chunkerPreservesAllText() {
        val text = "Первое предложение. Второе предложение! Третье?"
        val chunks = SpeechChunker.chunk(text, "req-1")
        val joined = chunks.joinToString(" ") { it.text }
        assertTrue(joined.contains("Первое"))
        assertTrue(joined.contains("Третье"))
    }

    @Test
    fun chunkerDoesNotCreateEmptyChunks() {
        val chunks = SpeechChunker.chunk("Одно короткое.", "req-2")
        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.all { it.text.isNotBlank() })
    }

    @Test
    fun decimalNumberNotSplit() {
        val text = "Температура 21,5 градуса. Дальше текст."
        val chunks = SpeechChunker.chunk(text, "req-3")
        assertTrue(chunks.any { it.text.contains("21,5") })
    }
}

class SpeechFailurePolicyTest {
    @Test
    fun piperLoadFailureShouldFallback() {
        assertTrue(SpeechFailurePolicy.shouldFallback(SpeechFailureCode.PIPER_MODEL_LOAD_FAILED))
    }

    @Test
    fun userListeningCancelSkipsFallback() {
        assertTrue(
            SpeechFailurePolicy.shouldSkipFallback(SpeechCancelReason.USER_STARTED_LISTENING),
        )
    }
}

class SpeechTuningTest {
    @Test
    fun defaultPiperSpeedIsSlowerPreset() {
        assertEquals(1.12f, SpeechSettings().resolvedPiperSpeed(), 0.001f)
    }

    @Test
    fun piperSpeedIsClamped() {
        assertEquals(1.35f, SpeechSettings(piperSpeed = 2.0f).resolvedPiperSpeed(), 0.001f)
        assertEquals(0.85f, SpeechSettings(piperSpeed = 0.5f).resolvedPiperSpeed(), 0.001f)
    }

    @Test
    fun defaultVoiceIsIrina() {
        assertEquals("ru_RU-irina-medium", SpeechSettings().preferredVoiceId)
    }

    @Test
    fun defaultAndroidTtsSpeechRateIsPointNine() {
        assertEquals(0.9f, SpeechSettings().androidTtsSpeechRate, 0.001f)
    }
}

class SpeechChunkPauseTest {
    @Test
    fun chunkerAssignsPauseBetweenChunks() {
        val text = "Первый абзац достаточно длинный для chunk. ${RussianSpeechTextNormalizer.PARAGRAPH_MARKER} Второй абзац тоже достаточно длинный для chunk."
        val chunks = SpeechChunker.chunk(text, "req-pause", chunkPauseMs = 110, paragraphPauseMs = 220)
        assertTrue(chunks.size >= 2)
        assertTrue(chunks.first().pauseAfterMs > 0)
        assertEquals(0, chunks.last().pauseAfterMs)
    }

    @Test
    fun paragraphPauseIsLongerThanChunkPause() {
        val text = "Раздел один содержит достаточно текста. ${RussianSpeechTextNormalizer.PARAGRAPH_MARKER} Раздел два содержит достаточно текста."
        val chunks = SpeechChunker.chunk(text, "req-para", chunkPauseMs = 110, paragraphPauseMs = 220)
        val paragraphPause = chunks.first { it.pauseAfterMs == 220 }
        assertTrue(paragraphPause.pauseAfterMs > 110)
    }
}
