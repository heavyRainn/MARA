package com.care.voice.platform.android.vision

import com.care.voice.brain.llm.LlmRole
import com.care.voice.brain.memory.MemoryMessage
import com.care.voice.brain.session.SessionManager
import com.care.voice.brain.vision.ConversationRole
import com.care.voice.brain.vision.ConversationTurn
import com.care.voice.platform.android.persistence.RoomConversationRepository
import java.time.Instant

class VisionContextLoader(
    private val sessionManager: SessionManager,
    private val conversationRepository: RoomConversationRepository,
) {
    suspend fun recentTurns(limit: Int = 4): List<ConversationTurn> {
        val sessionId = sessionManager.currentSessionId()
        return conversationRepository.loadTail(sessionId, limit).mapNotNull { message ->
            when (message.role) {
                LlmRole.USER -> ConversationTurn(ConversationRole.USER, message.content)
                LlmRole.ASSISTANT -> ConversationTurn(ConversationRole.ASSISTANT, message.content)
                LlmRole.SYSTEM -> null
            }
        }
    }

    suspend fun saveVisionExchange(userQuestion: String, assistantAnswer: String) {
        val sessionId = sessionManager.currentSessionId()
        sessionManager.touchActivity(Instant.now())
        conversationRepository.saveMessage(
            sessionId,
            MemoryMessage(role = LlmRole.USER, content = "[Фото] $userQuestion"),
        )
        conversationRepository.saveMessage(
            sessionId,
            MemoryMessage(role = LlmRole.ASSISTANT, content = assistantAnswer),
        )
    }
}
