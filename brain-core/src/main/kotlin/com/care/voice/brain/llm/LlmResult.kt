package com.care.voice.brain.llm

/**
 * Result of a language model call without throwing for expected failures.
 */
sealed class LlmResult<out T> {
    data class Success<T>(val value: T) : LlmResult<T>()

    data class Failure(val error: LlmError) : LlmResult<Nothing>()
}
