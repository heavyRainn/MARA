package com.care.voice.data.net

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VisionChatRequestDto(
    val model: String,
    val messages: List<VisionMessageDto>,
    val stream: Boolean = false,
    val temperature: Double = 0.3,
    @Json(name = "reasoning_effort")
    val reasoningEffort: String = "none",
    @Json(name = "reasoning_format")
    val reasoningFormat: String? = null,
    @Json(name = "max_completion_tokens")
    val maxCompletionTokens: Int = 500,
)

@JsonClass(generateAdapter = true)
data class VisionMessageDto(
    val role: String,
    val content: List<VisionContentDto>,
)

@JsonClass(generateAdapter = true)
data class VisionContentDto(
    val type: String,
    val text: String? = null,
    @Json(name = "image_url") val imageUrl: VisionImageUrlDto? = null,
)

@JsonClass(generateAdapter = true)
data class VisionImageUrlDto(
    val url: String,
)

@JsonClass(generateAdapter = true)
data class VisionChatResponseDto(
    val id: String? = null,
    val choices: List<VisionChoiceDto> = emptyList(),
    val created: Long? = null,
    val model: String? = null,
)

@JsonClass(generateAdapter = true)
data class VisionChoiceDto(
    val index: Int? = null,
    val message: VisionAssistantMessageDto? = null,
    @Json(name = "finish_reason") val finishReason: String? = null,
)

@JsonClass(generateAdapter = true)
data class VisionAssistantMessageDto(
    val role: String? = null,
    val content: String? = null,
    val reasoning: String? = null,
)
