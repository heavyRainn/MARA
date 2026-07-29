package com.care.voice.brain.vision

enum class ConversationRole {
    USER,
    ASSISTANT,
}

data class ConversationTurn(
    val role: ConversationRole,
    val content: String,
)

data class VisionImage(
    val jpegBytes: ByteArray,
    val dataUrl: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VisionImage) return false
        return jpegBytes.contentEquals(other.jpegBytes) && dataUrl == other.dataUrl
    }

    override fun hashCode(): Int {
        var result = jpegBytes.contentHashCode()
        result = 31 * result + dataUrl.hashCode()
        return result
    }
}

data class VisionRequest(
    val image: VisionImage,
    val question: String,
    val recentTurns: List<ConversationTurn> = emptyList(),
)

sealed interface VisionResult {
    data class Success(val text: String) : VisionResult

    data class Failure(
        val error: VisionError,
        val userMessage: String,
    ) : VisionResult
}

sealed interface VisionError {
    data object UriUnavailable : VisionError
    data object FileTooLarge : VisionError
    data object UnsupportedFormat : VisionError
    data object HttpBadRequest : VisionError
    data object Unauthorized : VisionError
    data object PayloadTooLarge : VisionError
    data object RateLimited : VisionError
    data object Timeout : VisionError
    data object EmptyResponse : VisionError
    data object Network : VisionError
    data class Unknown(val detail: String) : VisionError
}

enum class VisionUiState {
    Idle,
    PreparingImage,
    Analyzing,
    Success,
    Error,
}
