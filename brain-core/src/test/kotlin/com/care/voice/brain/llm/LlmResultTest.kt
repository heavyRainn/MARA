package com.care.voice.brain.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmResultTest {

    @Test
    fun successCarriesResponse() {
        val result = LlmResult.Success(LlmResponse("Ответ"))

        assertTrue(result is LlmResult.Success)
        assertEquals("Ответ", (result as LlmResult.Success).value.content)
    }

    @Test
    fun failureCarriesTypedError() {
        val result = LlmResult.Failure(LlmError.Unauthorized("Ключ API невалидный"))

        assertTrue(result is LlmResult.Failure)
        val error = (result as LlmResult.Failure).error
        assertTrue(error is LlmError.Unauthorized)
        assertEquals("Ключ API невалидный", error.message)
    }
}
