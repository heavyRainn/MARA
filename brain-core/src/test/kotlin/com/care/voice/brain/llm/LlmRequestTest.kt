package com.care.voice.brain.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class LlmRequestTest {

    @Test
    fun createsRequestWithMessagesAndTemperature() {
        val request = LlmRequest(
            messages = listOf(
                LlmMessage(LlmRole.SYSTEM, "Ты помощник."),
                LlmMessage(LlmRole.USER, "Привет")
            ),
            temperature = 0.2
        )

        assertEquals(2, request.messages.size)
        assertEquals(LlmRole.SYSTEM, request.messages[0].role)
        assertEquals("Привет", request.messages[1].content)
        assertEquals(0.2, request.temperature, 0.0)
    }

    @Test
    fun defaultTemperatureIsPointThree() {
        val request = LlmRequest(
            messages = listOf(LlmMessage(LlmRole.USER, "test"))
        )

        assertEquals(0.3, request.temperature, 0.0)
    }
}
