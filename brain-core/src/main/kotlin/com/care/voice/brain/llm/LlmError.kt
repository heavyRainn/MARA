package com.care.voice.brain.llm

/**
 * Typed error from a language model provider.
 */
sealed interface LlmError {
    val message: String

    data class Network(override val message: String) : LlmError

    data class Unauthorized(override val message: String) : LlmError

    data class RateLimited(override val message: String) : LlmError

    data class Server(override val message: String) : LlmError

    data class Unknown(override val message: String) : LlmError
}
