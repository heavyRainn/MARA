package com.care.voice.platform.android.vision

import com.care.voice.brain.vision.ConversationRole
import com.care.voice.brain.vision.VisionRequest
import com.care.voice.data.net.VisionChatRequestDto
import com.care.voice.data.net.VisionContentDto
import com.care.voice.data.net.VisionImageUrlDto
import com.care.voice.data.net.VisionMessageDto

internal fun VisionRequest.toVisionChatRequest(
    model: String,
    includeReasoningFormatHidden: Boolean = true,
): VisionChatRequestDto {
    val messages = buildList {
        add(
            VisionMessageDto(
                role = "system",
                content = listOf(
                    VisionContentDto(type = "text", text = VisionPrompt.SYSTEM),
                ),
            ),
        )
        recentTurns.forEach { turn ->
            add(
                VisionMessageDto(
                    role = turn.role.toWire(),
                    content = listOf(
                        VisionContentDto(type = "text", text = turn.content),
                    ),
                ),
            )
        }
        add(
            VisionMessageDto(
                role = "user",
                content = listOf(
                    VisionContentDto(type = "text", text = VisionPrompt.buildUserText(question)),
                    VisionContentDto(
                        type = "image_url",
                        imageUrl = VisionImageUrlDto(url = image.dataUrl),
                    ),
                ),
            ),
        )
    }
    return VisionChatRequestDto(
        model = model,
        messages = messages,
        stream = false,
        temperature = 0.3,
        reasoningEffort = "none",
        reasoningFormat = if (includeReasoningFormatHidden) "hidden" else null,
        maxCompletionTokens = 500,
    )
}

private fun ConversationRole.toWire(): String = when (this) {
    ConversationRole.USER -> "user"
    ConversationRole.ASSISTANT -> "assistant"
}
