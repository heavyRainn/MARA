package com.care.voice.brain.memory

import com.care.voice.brain.llm.LlmRole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeMemoryStoreTest {

    @Test
    fun storesAndLoadsConversationContextWithoutAndroidRuntime() = runTest {
        val store = FakeMemoryStore()
        val sessionId = "session-1"

        store.saveUserProfile(UserProfile(name = "Иван", age = 72))
        store.saveMessage(
            sessionId,
            MemoryMessage(role = LlmRole.USER, content = "Привет", timestampMillis = 100L)
        )
        store.saveSummary(
            ConversationSummary(
                sessionId = sessionId,
                text = "Пользователь поздоровался.",
                messageCountAtSummary = 1
            )
        )

        val context = store.loadConversationContext(sessionId)

        assertEquals(sessionId, context.sessionId)
        assertEquals("Иван", context.profile.name)
        assertEquals(72, context.profile.age)
        assertEquals(1, context.recentMessages.size)
        assertEquals(LlmRole.USER, context.recentMessages[0].role)
        assertEquals("Пользователь поздоровался.", context.summary)
    }

    @Test
    fun emptySessionHasNoMessagesOrSummary() = runTest {
        val store = FakeMemoryStore()

        val context = store.loadConversationContext("new-session")

        assertEquals(0, context.recentMessages.size)
        assertNull(context.summary)
        assertEquals(UserProfile(), context.profile)
    }
}
