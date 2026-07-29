package com.care.voice.platform.android.vision

import com.care.voice.brain.vision.VisionError
import com.care.voice.brain.vision.VisionImage
import com.care.voice.brain.vision.VisionRequest
import com.care.voice.brain.vision.VisionResult
import com.care.voice.data.net.VisionApi
import com.care.voice.data.net.VisionAssistantMessageDto
import com.care.voice.data.net.VisionChatRequestDto
import com.care.voice.data.net.VisionChatResponseDto
import com.care.voice.data.net.VisionChoiceDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class GroqVisionProviderTest {

    @Test
    fun emptyResponseIsFailure() = runTest {
        val provider = GroqVisionProvider(
            api = SequenceVisionApi(listOf("   ", "   ")),
            model = GroqVisionProvider.GROQ_VISION_MODEL,
        )
        val result = provider.analyze(sampleRequest())
        assertTrue(result is VisionResult.Failure)
        assertEquals(VisionError.EmptyResponse, (result as VisionResult.Failure).error)
        assertEquals(VisionPrompt.EMPTY_AFTER_SANITIZE_MESSAGE, result.userMessage)
    }

    @Test
    fun successfulResponseReturnsText() = runTest {
        val provider = GroqVisionProvider(
            api = SequenceVisionApi(listOf("На фото белая компьютерная мышь.")),
            model = GroqVisionProvider.GROQ_VISION_MODEL,
        )
        val result = provider.analyze(sampleRequest())
        assertTrue(result is VisionResult.Success)
        assertEquals(
            "На фото белая компьютерная мышь.",
            (result as VisionResult.Success).text,
        )
    }

    @Test
    fun thinkBlockIsStrippedBeforeSuccess() = runTest {
        val raw = """
            <think>English internal reasoning...</think>
            На фотографии компьютерная мышь.
        """.trimIndent()
        val provider = GroqVisionProvider(
            api = SequenceVisionApi(listOf(raw)),
            model = GroqVisionProvider.GROQ_VISION_MODEL,
        )
        val result = provider.analyze(sampleRequest())
        assertTrue(result is VisionResult.Success)
        assertEquals(
            "На фотографии компьютерная мышь.",
            (result as VisionResult.Success).text,
        )
        assertFalse(result.text.contains("think", ignoreCase = true))
        assertFalse(result.text.contains("English"))
    }

    @Test
    fun separateReasoningFieldNeverLeaks() = runTest {
        val api = object : VisionApi {
            override suspend fun chat(req: VisionChatRequestDto): VisionChatResponseDto =
                VisionChatResponseDto(
                    choices = listOf(
                        VisionChoiceDto(
                            message = VisionAssistantMessageDto(
                                role = "assistant",
                                content = "На фото мышь.",
                                reasoning = "Secret English chain of thought",
                            ),
                        ),
                    ),
                )
        }
        val result = GroqVisionProvider(api, GroqVisionProvider.GROQ_VISION_MODEL)
            .analyze(sampleRequest())
        assertTrue(result is VisionResult.Success)
        assertEquals("На фото мышь.", (result as VisionResult.Success).text)
        assertFalse(result.text.contains("Secret"))
    }

    @Test
    fun emptyAfterSanitizeRetriesOnceThenFails() = runTest {
        val calls = AtomicInteger(0)
        val api = object : VisionApi {
            override suspend fun chat(req: VisionChatRequestDto): VisionChatResponseDto {
                calls.incrementAndGet()
                return VisionChatResponseDto(
                    choices = listOf(
                        VisionChoiceDto(
                            message = VisionAssistantMessageDto(
                                role = "assistant",
                                content = "<think>only reasoning, no final answer",
                            ),
                        ),
                    ),
                )
            }
        }
        val result = GroqVisionProvider(api, GroqVisionProvider.GROQ_VISION_MODEL)
            .analyze(sampleRequest())

        assertEquals(2, calls.get())
        assertTrue(result is VisionResult.Failure)
        assertEquals(
            VisionPrompt.EMPTY_AFTER_SANITIZE_MESSAGE,
            (result as VisionResult.Failure).userMessage,
        )
    }

    @Test
    fun emptyAfterSanitizeSucceedsOnRetry() = runTest {
        val api = SequenceVisionApi(
            listOf(
                "<think>only reasoning",
                "Похоже, это зарядное устройство.",
            ),
        )
        val result = GroqVisionProvider(api, GroqVisionProvider.GROQ_VISION_MODEL)
            .analyze(sampleRequest())
        assertTrue(result is VisionResult.Success)
        assertEquals(
            "Похоже, это зарядное устройство.",
            (result as VisionResult.Success).text,
        )
    }

    @Test
    fun requestUsesReasoningEffortNone() = runTest {
        var captured: VisionChatRequestDto? = null
        val api = object : VisionApi {
            override suspend fun chat(req: VisionChatRequestDto): VisionChatResponseDto {
                captured = req
                return VisionChatResponseDto(
                    choices = listOf(
                        VisionChoiceDto(
                            message = VisionAssistantMessageDto(
                                role = "assistant",
                                content = "Ок.",
                            ),
                        ),
                    ),
                )
            }
        }
        GroqVisionProvider(api, GroqVisionProvider.GROQ_VISION_MODEL).analyze(sampleRequest())
        assertEquals("none", captured?.reasoningEffort)
        assertFalse(captured?.reasoningFormat == "raw")
    }

    private fun sampleRequest() = VisionRequest(
        image = VisionImage(
            jpegBytes = byteArrayOf(1, 2, 3),
            dataUrl = "data:image/jpeg;base64,AQID",
        ),
        question = "Что это?",
    )

    private class SequenceVisionApi(
        private val responses: List<String>,
    ) : VisionApi {
        private var index = 0

        override suspend fun chat(req: VisionChatRequestDto): VisionChatResponseDto {
            val text = responses[index.coerceAtMost(responses.lastIndex)]
            index++
            return VisionChatResponseDto(
                choices = listOf(
                    VisionChoiceDto(
                        message = VisionAssistantMessageDto(
                            role = "assistant",
                            content = text,
                        ),
                    ),
                ),
            )
        }
    }
}
