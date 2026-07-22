package com.care.voice.brain

import com.care.voice.brain.memory.FakeMemoryStore
import com.care.voice.brain.test.OrchestratorTestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AssistantOrchestratorTest {

    @Test
    fun confirmationFlowSchedulesOnceAndClearsPending() = runTest {
        val memory = FakeMemoryStore()
        val scheduleCount = AtomicInteger(0)
        val orchestrator = OrchestratorTestFixtures.createOrchestrator(
            memory = memory,
            scheduleCount = scheduleCount,
            scheduleFails = false
        )

        val ask = orchestrator.handle(AssistantInput.UserMessage("напомни завтра в 9 выпить таблетку"))
        assertTrue(ask is AssistantResult.ConfirmationRequired)

        val confirm = orchestrator.handle(AssistantInput.UserMessage("да"))
        assertTrue(confirm is AssistantResult.ActionCompleted)
        assertEquals(1, scheduleCount.get())

        val confirmAgain = orchestrator.handle(AssistantInput.UserMessage("да"))
        assertTrue(confirmAgain is AssistantResult.Reply || confirmAgain is AssistantResult.Failure)
        assertEquals(1, scheduleCount.get())
    }

    @Test
    fun cancelPendingDoesNotSchedule() = runTest {
        val scheduleCount = AtomicInteger(0)
        val orchestrator = OrchestratorTestFixtures.createOrchestrator(scheduleCount = scheduleCount)
        orchestrator.handle(AssistantInput.UserMessage("напомни завтра в 9 выпить таблетку"))
        val cancel = orchestrator.handle(AssistantInput.UserMessage("нет"))
        assertTrue(cancel is AssistantResult.ActionCancelled)
        assertEquals(0, scheduleCount.get())
    }

    @Test
    fun yesWithoutPendingIsNormalChat() = runTest {
        val scheduleCount = AtomicInteger(0)
        val orchestrator = OrchestratorTestFixtures.createOrchestrator(scheduleCount = scheduleCount)
        val result = orchestrator.handle(AssistantInput.UserMessage("да"))
        assertTrue(result is AssistantResult.Reply)
        assertEquals(0, scheduleCount.get())
    }

    @Test
    fun schedulerFailureAllowsRetry() = runTest {
        val scheduleCount = AtomicInteger(0)
        val orchestrator = OrchestratorTestFixtures.createOrchestrator(
            scheduleCount = scheduleCount,
            scheduleFails = true
        )
        orchestrator.handle(AssistantInput.UserMessage("напомни завтра в 9 выпить таблетку"))
        val failed = orchestrator.handle(AssistantInput.UserMessage("да"))
        assertTrue(failed is AssistantResult.Failure)
        assertEquals(1, scheduleCount.get())
    }

    @Test
    fun newRequestWhilePendingClearsOldPending() = runTest {
        val scheduleCount = AtomicInteger(0)
        val orchestrator = OrchestratorTestFixtures.createOrchestrator(scheduleCount = scheduleCount)
        orchestrator.handle(AssistantInput.UserMessage("напомни завтра в 9 выпить таблетку"))
        val chat = orchestrator.handle(AssistantInput.UserMessage("какая сегодня погода"))
        assertTrue(chat is AssistantResult.Reply)
        val confirmOld = orchestrator.handle(AssistantInput.UserMessage("да"))
        assertTrue(confirmOld is AssistantResult.Reply)
        assertEquals(0, scheduleCount.get())
    }

    @Test
    fun llmFailureDoesNotSaveAssistantMessage() = runTest {
        val memory = FakeMemoryStore()
        val conversation = com.care.voice.brain.memory.FakeConversationRepository()
        val pending = com.care.voice.brain.pending.FakePendingActionRepository()
        val failingLm = object : com.care.voice.brain.llm.LanguageModel {
            override suspend fun generate(request: com.care.voice.brain.llm.LlmRequest) =
                com.care.voice.brain.llm.LlmResult.Failure(com.care.voice.brain.llm.LlmError.Network("offline"))
        }
        val orchestrator = AssistantOrchestrator(
            languageModel = failingLm,
            memoryStore = memory,
            conversationRepository = conversation,
            reminderScheduler = object : com.care.voice.brain.reminder.ReminderScheduler {
                override suspend fun schedule(request: com.care.voice.brain.reminder.ReminderRequest) =
                    com.care.voice.brain.reminder.ReminderScheduleResult.Success(1L)
            },
            sessionManager = com.care.voice.brain.session.FakeSessionManager(),
            pendingActionRepository = pending,
            memoryPipeline = com.care.voice.brain.memory.pipeline.MemoryPipeline(
                extractor = object : com.care.voice.brain.memory.extract.MemoryExtractor {
                    override suspend fun extract(
                        userMessage: com.care.voice.brain.memory.MemoryMessage,
                        recentContext: List<com.care.voice.brain.memory.MemoryMessage>
                    ): List<com.care.voice.brain.memory.fact.MemoryCandidate> = emptyList()
                },
                memoryRepository = com.care.voice.brain.memory.FakeMemoryRepository(),
                conversationRepository = conversation,
                summaryRepository = object : com.care.voice.brain.memory.SummaryRepository {
                    override suspend fun load(sessionId: String) = null
                    override suspend fun save(summary: com.care.voice.brain.memory.ConversationSummary) = Unit
                },
                memoryStore = memory,
                pendingActionRepository = pending
            ),
            reminderIntentResolver = com.care.voice.brain.reminder.ReminderIntentResolver(failingLm),
            conversationSummarizer = com.care.voice.brain.summary.ConversationSummarizer(failingLm, memory)
        )
        val result = orchestrator.handle(AssistantInput.UserMessage("привет"))
        assertTrue(result is AssistantResult.Failure)
        assertEquals(0, conversation.countActiveMessages("test-session"))
    }
}
