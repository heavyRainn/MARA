package com.care.voice.brain.test

import com.care.voice.brain.AssistantOrchestrator
import com.care.voice.brain.llm.LanguageModel
import com.care.voice.brain.llm.LlmRequest
import com.care.voice.brain.llm.LlmResponse
import com.care.voice.brain.llm.LlmResult
import com.care.voice.brain.memory.FakeConversationRepository
import com.care.voice.brain.memory.FakeMemoryRepository
import com.care.voice.brain.memory.FakeMemoryStore
import com.care.voice.brain.memory.extract.MemoryExtractor
import com.care.voice.brain.memory.pipeline.MemoryPipeline
import com.care.voice.brain.pending.FakePendingActionRepository
import com.care.voice.brain.reminder.ReminderCoordinator
import com.care.voice.brain.reminder.ReminderIntentResolver
import com.care.voice.brain.reminder.ReminderRequest
import com.care.voice.brain.reminder.ScheduleReminderResult
import com.care.voice.brain.reminder.ReminderScheduler
import com.care.voice.brain.reminder.ReminderTimeParser
import com.care.voice.brain.session.FakeSessionManager
import com.care.voice.brain.summary.ConversationSummarizer
import java.util.concurrent.atomic.AtomicInteger

object OrchestratorTestFixtures {

    fun createOrchestrator(
        memory: FakeMemoryStore = FakeMemoryStore(),
        conversation: FakeConversationRepository = FakeConversationRepository(),
        pending: FakePendingActionRepository = FakePendingActionRepository(),
        session: FakeSessionManager = FakeSessionManager("test-session"),
        now: Long = 1_700_000_000_000L,
        scheduleCount: AtomicInteger = AtomicInteger(0),
        scheduleFails: Boolean = false,
        memoryExtractor: MemoryExtractor? = null
    ): AssistantOrchestrator {
        val languageModel = object : LanguageModel {
            override suspend fun generate(request: LlmRequest): LlmResult<LlmResponse> {
                val system = request.messages.firstOrNull()?.content.orEmpty()
                val user = request.messages.lastOrNull()?.content.orEmpty()
                return when {
                    system.contains("СОЗДАТЬ напоминание") && user.contains("напомни") -> LlmResult.Success(
                        LlmResponse(
                            """{"isReminder":true,"text":"выпить таблетку","timeExpression":"завтра в 9","repeatExpression":null}"""
                        )
                    )
                    else -> LlmResult.Success(LlmResponse("Обычный ответ."))
                }
            }
        }

        val reminderScheduler = object : ReminderScheduler {
            override suspend fun schedule(request: ReminderRequest): ScheduleReminderResult {
                scheduleCount.incrementAndGet()
                return if (scheduleFails) {
                    ScheduleReminderResult.Failure("alarm failed")
                } else {
                    ScheduleReminderResult.Success(1L, System.currentTimeMillis(), request.precision)
                }
            }
        }

        val memoryRepository = FakeMemoryRepository()
        val memoryPipeline = MemoryPipeline(
            extractor = memoryExtractor ?: object : MemoryExtractor {
                override suspend fun extract(
                    userMessage: com.care.voice.brain.memory.MemoryMessage,
                    recentContext: List<com.care.voice.brain.memory.MemoryMessage>
                ) = emptyList<com.care.voice.brain.memory.fact.MemoryCandidate>()
            },
            memoryRepository = memoryRepository,
            conversationRepository = conversation,
            summaryRepository = object : com.care.voice.brain.memory.SummaryRepository {
                override suspend fun load(sessionId: String) = memory.loadConversationContext(sessionId, 8).let {
                    if (it.summary == null) null else com.care.voice.brain.memory.ConversationSummary(sessionId, it.summary!!, it.messageCountAtSummary)
                }
                override suspend fun save(summary: com.care.voice.brain.memory.ConversationSummary) {
                    memory.saveSummary(summary)
                }
            },
            memoryStore = memory,
            pendingActionRepository = pending
        )

        return AssistantOrchestrator(
            languageModel = languageModel,
            memoryStore = memory,
            conversationRepository = conversation,
            reminderScheduler = reminderScheduler,
            sessionManager = session,
            pendingActionRepository = pending,
            memoryPipeline = memoryPipeline,
            reminderIntentResolver = ReminderIntentResolver(languageModel) { now },
            conversationSummarizer = ConversationSummarizer(languageModel, memory),
            reminderCoordinator = ReminderCoordinator(
                ReminderTimeParser(nowProvider = { now }),
                nowMillis = { now }
            ),
            nowProvider = { java.time.Instant.ofEpochMilli(now) }
        )
    }
}
