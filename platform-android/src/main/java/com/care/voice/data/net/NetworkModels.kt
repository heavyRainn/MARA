package com.care.voice.data.net

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean = false,
    val temperature: Double = 0.3
)

@JsonClass(generateAdapter = true)
data class Message(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class ChatResponse(
    val id: String? = null,
    val choices: List<Choice> = emptyList(),
    val created: Long? = null,
    val model: String? = null,
    val object_: String? = null
) {
    @JsonClass(generateAdapter = true)
    data class Choice(
        val index: Int? = null,
        val message: Message? = null,
        @Json(name = "finish_reason") val finishReason: String? = null
    )
}

@JsonClass(generateAdapter = true)
data class ErrorBody(
    val error: ErrorDetail?
) {
    @JsonClass(generateAdapter = true)
    data class ErrorDetail(
        val message: String?,
        val type: String? = null,
        val code: String? = null
    )
}
