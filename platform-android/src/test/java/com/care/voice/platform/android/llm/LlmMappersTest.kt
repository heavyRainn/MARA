package com.care.voice.platform.android.llm

import com.care.voice.brain.llm.LlmMessage
import com.care.voice.brain.llm.LlmRequest
import com.care.voice.brain.llm.LlmRole
import org.junit.Assert.assertEquals
import org.junit.Test

class LlmMappersTest {

    @Test
    fun mapsRolesToWireFormat() {
        val request = LlmRequest(
            messages = listOf(
                LlmMessage(LlmRole.SYSTEM, "system"),
                LlmMessage(LlmRole.USER, "user"),
                LlmMessage(LlmRole.ASSISTANT, "assistant")
            ),
            temperature = 0.2
        )

        val network = request.toNetwork("llama-test")
        assertEquals("llama-test", network.model)
        assertEquals(0.2, network.temperature, 0.001)
        assertEquals(listOf("system", "user", "assistant"), network.messages.map { it.role })
    }
}
