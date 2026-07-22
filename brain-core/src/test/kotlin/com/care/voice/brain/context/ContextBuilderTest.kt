package com.care.voice.brain.context

import com.care.voice.brain.llm.LlmRole
import com.care.voice.brain.memory.ConversationMemory
import com.care.voice.brain.memory.MemoryMessage
import com.care.voice.brain.memory.UserProfile
import com.care.voice.brain.memory.fact.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ContextBuilderTest {

    private val builder = ContextBuilder()

    @Test
    fun buildChatRequestIncludesSystemSafetySummaryAndUserMessage() {
        val memory = ConversationMemory(
            sessionId = "s1",
            recentMessages = listOf(
                MemoryMessage(role = LlmRole.USER, content = "Привет"),
                MemoryMessage(role = LlmRole.ASSISTANT, content = "Здравствуйте")
            ),
            summary = "Пользователь поздоровался.",
            messageCountAtSummary = 2,
            profile = UserProfile(name = "Мария", age = 75)
        )
        val fact = MemoryFact(
            id = "1",
            subject = MemorySubject.User,
            type = MemoryType.IDENTITY,
            key = "name",
            value = "Мария",
            confidence = 1.0,
            importance = 8,
            validFrom = null,
            validUntil = null,
            status = MemoryStatus.ACTIVE,
            confirmationStatus = MemoryConfirmationStatus.CONFIRMED,
            sensitivity = MemorySensitivity.NORMAL,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            lastConfirmedAt = Instant.EPOCH,
            lastUsedAt = null
        )

        val messages = builder.buildChatRequest(
            memory = memory,
            userText = "Как дела?",
            relevantFacts = listOf(fact),
            currentMessageOverrides = builder.currentMessagePriorityNote("Как дела?")
        )

        assertTrue(messages[0].content.contains("пожилых"))
        assertTrue(messages.any { it.content.contains("Мария") })
        assertTrue(messages.any { it.content.contains("резюме") })
        assertTrue(messages.any { it.content == "Привет" })
        assertEquals("Как дела?", messages.last().content)
    }

    @Test
    fun profileContextEmptyForDefaultProfile() {
        assertEquals("", builder.profileContext(UserProfile()))
    }

    @Test
    fun emptySummaryAndProfileAreOmittedFromTail() {
        val memory = ConversationMemory(
            sessionId = "s1",
            recentMessages = emptyList(),
            summary = "  ",
            messageCountAtSummary = 0,
            profile = UserProfile()
        )
        val messages = builder.buildChatRequest(memory, "Привет")
        assertEquals(LlmRole.USER, messages.last().role)
        assertEquals("Привет", messages.last().content)
        assertTrue(messages.first().role == LlmRole.SYSTEM)
    }

    @Test
    fun currentUserMessageIsNotDuplicatedInHistoryTail() {
        val memory = ConversationMemory(
            sessionId = "s1",
            recentMessages = listOf(
                MemoryMessage(role = LlmRole.USER, content = "Старое"),
                MemoryMessage(role = LlmRole.ASSISTANT, content = "Ответ")
            ),
            summary = null,
            messageCountAtSummary = 0,
            profile = UserProfile()
        )
        val messages = builder.buildChatRequest(memory, "Новый вопрос")
        assertFalse(messages.any { it.role == LlmRole.USER && it.content == "Новый вопрос" && messages.indexOf(it) != messages.lastIndex })
        assertEquals("Новый вопрос", messages.last().content)
    }
}
