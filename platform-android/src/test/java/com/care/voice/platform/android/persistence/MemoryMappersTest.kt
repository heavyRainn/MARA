package com.care.voice.platform.android.persistence

import com.care.voice.brain.llm.LlmRole
import com.care.voice.brain.memory.ConversationSummary
import com.care.voice.brain.memory.MemoryMessage
import com.care.voice.brain.memory.UserProfile
import com.care.voice.data.history.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryMappersTest {

    @Test
    fun messageEntityToBrainPreservesRoleAndContent() {
        val entity = MessageEntity(
            sessionId = "s1",
            messageUid = "uid-1",
            role = "assistant",
            content = "Ответ",
            ts = 42L
        )
        val brain = entity.toBrain()
        assertEquals(LlmRole.ASSISTANT, brain.role)
        assertEquals("Ответ", brain.content)
        assertEquals(42L, brain.timestampMillis)
        assertEquals("uid-1", brain.id)
    }

    @Test
    fun userProfileRoundTrip() {
        val profile = UserProfile(name = "Иван", age = 70, notes = "любит чай")
        val entity = profile.toEntity()
        val back = entity.toBrain()
        assertEquals(profile, back)
    }

    @Test
    fun summaryRoundTrip() {
        val summary = ConversationSummary(sessionId = "s1", text = "Резюме", messageCountAtSummary = 12)
        val entity = summary.toEntity()
        val back = entity.toBrain()
        assertEquals(summary, back)
    }
}
