package com.care.voice.platform.android.vision

import com.care.voice.brain.vision.VisionImage
import com.care.voice.brain.vision.VisionRequest
import com.care.voice.data.net.VisionChatRequestDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionMappersTest {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(VisionChatRequestDto::class.java)

    @Test
    fun visionJsonContainsImageUrlAndQwenModel() {
        val request = VisionRequest(
            image = VisionImage(
                jpegBytes = byteArrayOf(1, 2, 3),
                dataUrl = "data:image/jpeg;base64,QUJD",
            ),
            question = "Что это?",
        )
        val dto = request.toVisionChatRequest(GroqVisionProvider.GROQ_VISION_MODEL)
        val json = adapter.toJson(dto)

        assertEquals(GroqVisionProvider.GROQ_VISION_MODEL, dto.model)
        assertTrue(json.contains("\"type\":\"image_url\""))
        assertTrue(json.contains("\"type\":\"text\""))
        assertTrue(json.contains("data:image/jpeg;base64,QUJD"))
        assertTrue(json.contains("Что это?"))
        assertFalse(json.contains("llama-3.1-8b-instant"))
    }

    @Test
    fun visionJsonUsesNonThinkingMode() {
        val dto = sampleRequest().toVisionChatRequest(GroqVisionProvider.GROQ_VISION_MODEL)
        val json = adapter.toJson(dto)

        assertEquals("none", dto.reasoningEffort)
        assertTrue(json.contains("\"reasoning_effort\":\"none\""))
        assertFalse(json.contains("\"reasoning_format\":\"raw\""))
        assertEquals("hidden", dto.reasoningFormat)
        assertTrue(json.contains("\"reasoning_format\":\"hidden\""))
        assertEquals(500, dto.maxCompletionTokens)
        assertTrue(json.contains("\"max_completion_tokens\":500"))
    }

    @Test
    fun reasoningFormatNullIsOmittedFromJson() {
        val dto = sampleRequest().toVisionChatRequest(
            model = GroqVisionProvider.GROQ_VISION_MODEL,
            includeReasoningFormatHidden = false,
        )
        val json = adapter.toJson(dto)

        assertEquals("none", dto.reasoningEffort)
        assertTrue(json.contains("\"reasoning_effort\":\"none\""))
        assertFalse(json.contains("reasoning_format"))
    }

    @Test
    fun userTextContainsFinalOnlyInstruction() {
        val dto = sampleRequest().toVisionChatRequest(GroqVisionProvider.GROQ_VISION_MODEL)
        val userText = dto.messages.last().content.first { it.type == "text" }.text.orEmpty()

        assertTrue(userText.contains("Ответь только итоговым ответом на русском языке"))
        assertTrue(userText.contains("теги think"))
        assertTrue(userText.contains("Что это?"))
    }

    @Test
    fun visionModelDiffersFromTextChatModel() {
        assertEquals("qwen/qwen3.6-27b", GroqVisionProvider.GROQ_VISION_MODEL)
        assertEquals("llama-3.1-8b-instant", TEXT_CHAT_MODEL)
        assertFalse(GroqVisionProvider.GROQ_VISION_MODEL.contains("llama"))
    }

    private fun sampleRequest() = VisionRequest(
        image = VisionImage(
            jpegBytes = byteArrayOf(1, 2, 3),
            dataUrl = "data:image/jpeg;base64,QUJD",
        ),
        question = "Что это?",
    )

    companion object {
        private const val TEXT_CHAT_MODEL = "llama-3.1-8b-instant"
    }
}
