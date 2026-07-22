package com.care.voice.brain.memory.pipeline

import com.care.voice.brain.llm.LlmRole
import com.care.voice.brain.memory.FakeConversationRepository
import com.care.voice.brain.memory.FakeMemoryRepository
import com.care.voice.brain.memory.FakeMemoryStore
import com.care.voice.brain.memory.MemoryMessage
import com.care.voice.brain.memory.extract.MemoryExtractor
import com.care.voice.brain.memory.fact.*
import com.care.voice.brain.pending.FakePendingActionRepository
import com.care.voice.brain.pending.PendingActionState
import com.care.voice.brain.pending.PendingActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MemoryPipelineTest {

    private val now = Instant.parse("2026-07-22T10:00:00Z")
    private val conversation = FakeConversationRepository()
    private val memoryRepository = FakeMemoryRepository()
    private val memoryStore = FakeMemoryStore()
    private val pending = FakePendingActionRepository()

    @Test
    fun medicationRequiresConfirmationBeforeApply() = kotlinx.coroutines.runBlocking {
        val pipeline = pipelineWith(
            listOf(
                candidate(MemoryType.MEDICATION, "аспирин", "medication")
            )
        )
        val result = pipeline.processUserTurn(
            "s1",
            MemoryMessage(id = "m1", role = LlmRole.USER, content = "Я принимаю аспирин"),
            excludeExtraction = false
        )
        assertNotNull(result.pendingActionId)
        assertTrue(result.confirmationPrompt!!.contains("аспирин"))
        assertEquals(0, result.appliedCount)
        assertTrue(memoryRepository.facts.isEmpty())
    }

    @Test
    fun confirmedMedicationCreatesActiveFact() = kotlinx.coroutines.runBlocking {
        val pipeline = pipelineWith(
            listOf(candidate(MemoryType.MEDICATION, "аспирин", "medication"))
        )
        val turn = pipeline.processUserTurn(
            "s1",
            MemoryMessage(id = "m1", role = LlmRole.USER, content = "Я принимаю аспирин"),
            excludeExtraction = false
        )
        val action = pending.loadActive()!!
        assertEquals(PendingActionType.CONFIRM_MEMORY, action.type)
        val ok = pipeline.applyConfirmedMemory(action)
        assertTrue(ok)
        assertTrue(memoryRepository.facts.any { it.type == MemoryType.MEDICATION && it.status == MemoryStatus.ACTIVE })
    }

    @Test
    fun rejectedMemoryDoesNotApply() = kotlinx.coroutines.runBlocking {
        val pipeline = pipelineWith(
            listOf(candidate(MemoryType.MEDICATION, "аспирин", "medication"))
        )
        pipeline.processUserTurn(
            "s1",
            MemoryMessage(id = "m1", role = LlmRole.USER, content = "Я принимаю аспирин"),
            excludeExtraction = false
        )
        val action = pending.loadActive()!!
        pipeline.rejectPendingMemory(action)
        assertTrue(memoryRepository.facts.isEmpty())
        assertEquals(PendingActionState.REJECTED, pending.getById(action.id)!!.state)
    }

    @Test
    fun identityAutoApplies() = kotlinx.coroutines.runBlocking {
        val pipeline = pipelineWith(
            listOf(candidate(MemoryType.IDENTITY, "Анна", "name"))
        )
        val result = pipeline.processUserTurn(
            "s1",
            MemoryMessage(id = "m1", role = LlmRole.USER, content = "Меня зовут Анна"),
            excludeExtraction = false
        )
        assertEquals(1, result.appliedCount)
        assertTrue(memoryRepository.facts.any { it.value == "Анна" })
    }

    @Test
    fun excludeExtractionSkipsPipeline() = kotlinx.coroutines.runBlocking {
        val pipeline = pipelineWith(listOf(candidate(MemoryType.IDENTITY, "Анна", "name")))
        val result = pipeline.processUserTurn(
            "s1",
            MemoryMessage(id = "m1", role = LlmRole.USER, content = "Меня зовут Анна"),
            excludeExtraction = true
        )
        assertEquals(0, result.appliedCount)
        assertTrue(memoryRepository.facts.isEmpty())
    }

    @Test
    fun archivedMessagesNotInTail() = kotlinx.coroutines.runBlocking {
        repeat(10) { i ->
            conversation.saveMessage(
                "s1",
                MemoryMessage(id = "m$i", role = LlmRole.USER, content = "msg$i")
            )
        }
        conversation.archiveOldMessages("s1", keepActive = 8)
        val tail = conversation.loadTail("s1", 20)
        assertEquals(8, tail.size)
        assertTrue(tail.all { it.state == com.care.voice.brain.memory.MessageState.ACTIVE })
        val all = conversation.loadMessagesForSummary("s1", null)
        assertEquals(10, all.size)
    }

    private fun pipelineWith(candidates: List<MemoryCandidate>) = MemoryPipeline(
        extractor = object : MemoryExtractor {
            override suspend fun extract(userMessage: MemoryMessage, recentContext: List<MemoryMessage>) =
                candidates
        },
        memoryRepository = memoryRepository,
        conversationRepository = conversation,
        summaryRepository = object : com.care.voice.brain.memory.SummaryRepository {
            override suspend fun load(sessionId: String) = null
            override suspend fun save(summary: com.care.voice.brain.memory.ConversationSummary) {}
        },
        memoryStore = memoryStore,
        pendingActionRepository = pending,
        nowProvider = { now }
    )

    private fun candidate(type: MemoryType, value: String, key: String) = MemoryCandidate(
        operation = MemoryOperation.ADD,
        subject = MemorySubject.User,
        type = type,
        key = key,
        value = value,
        confidence = 0.95,
        sensitivity = if (type in MEDICAL) MemorySensitivity.HEALTH else MemorySensitivity.NORMAL,
        requiresConfirmation = type in MEDICAL,
        validFrom = null,
        validUntil = null,
        reason = "explicit"
    )

    companion object {
        private val MEDICAL = setOf(
            MemoryType.MEDICATION,
            MemoryType.MEDICATION_DOSAGE,
            MemoryType.ALLERGY,
            MemoryType.HEALTH_CONDITION,
            MemoryType.CARE_INSTRUCTION
        )
    }
}
