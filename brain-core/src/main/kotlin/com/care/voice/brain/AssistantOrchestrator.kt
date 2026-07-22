package com.care.voice.brain

import com.care.voice.brain.context.ContextBuilder
import com.care.voice.brain.llm.LanguageModel
import com.care.voice.brain.llm.LlmRequest
import com.care.voice.brain.llm.LlmResult
import com.care.voice.brain.llm.toAssistantError
import com.care.voice.brain.llm.toUserMessage
import com.care.voice.brain.memory.ConversationRepository
import com.care.voice.brain.memory.MemoryMessage
import com.care.voice.brain.memory.MemoryStore
import com.care.voice.brain.memory.fact.ForgetMemoryRequest
import com.care.voice.brain.memory.fact.MemorySubject
import com.care.voice.brain.memory.fact.MemoryTopicHint
import com.care.voice.brain.memory.fact.MemoryType
import com.care.voice.brain.memory.pipeline.MemoryPipeline
import com.care.voice.brain.memory.retrieve.MemoryRetriever
import com.care.voice.brain.pending.PendingAction
import com.care.voice.brain.pending.PendingActionCodec
import com.care.voice.brain.pending.PendingActionRepository
import com.care.voice.brain.pending.PendingActionState
import com.care.voice.brain.pending.PendingActionType
import com.care.voice.brain.reminder.ReminderCoordinator
import com.care.voice.brain.reminder.ReminderIntentResolver
import com.care.voice.brain.reminder.ReminderScheduler
import com.care.voice.brain.session.SessionManager
import com.care.voice.brain.summary.ConversationSummarizer
import com.care.voice.brain.util.ConfirmationMatcher
import com.care.voice.brain.llm.LlmRole
import kotlinx.coroutines.CancellationException
import java.time.Instant

class AssistantOrchestrator(
    private val languageModel: LanguageModel,
    private val memoryStore: MemoryStore,
    private val conversationRepository: ConversationRepository,
    private val reminderScheduler: ReminderScheduler,
    private val sessionManager: SessionManager,
    private val pendingActionRepository: PendingActionRepository,
    private val memoryPipeline: MemoryPipeline,
    private val reminderIntentResolver: ReminderIntentResolver,
    private val conversationSummarizer: ConversationSummarizer,
    private val contextBuilder: ContextBuilder = ContextBuilder(),
    private val reminderCoordinator: ReminderCoordinator = ReminderCoordinator(),
    private val memoryRetriever: MemoryRetriever = MemoryRetriever(),
    private val historyTail: Int = 8,
    private val nowProvider: () -> Instant = { Instant.now() },
    private val onMemoryChanged: () -> Unit = {}
) {

    suspend fun handle(input: AssistantInput): AssistantResult {
        return try {
            pendingActionRepository.expireOld(nowProvider())
            when (input) {
                is AssistantInput.UserMessage -> handleUserMessage(input.text.trim())
                is AssistantInput.ConfirmationReceived -> handleConfirmation(input.accepted)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AssistantResult.Failure(
                error = AssistantError.Unexpected(),
                userMessage = "Произошла ошибка. Попробуйте ещё раз."
            )
        }
    }

    private suspend fun handleUserMessage(text: String): AssistantResult {
        if (text.isBlank()) {
            return AssistantResult.Failure(
                error = AssistantError.InvalidModelResponse,
                userMessage = "Не удалось распознать сообщение."
            )
        }

        val sessionId = sessionManager.currentSessionId()
        sessionManager.touchActivity(nowProvider())

        handleForgetCommands(text)?.let { return it }

        pendingActionRepository.loadActive()?.let { pending ->
            if (PendingActionCodec.isExpired(pending, nowProvider())) {
                pendingActionRepository.updateState(pending.id, PendingActionState.EXPIRED)
            } else {
                when {
                    ConfirmationMatcher.isYes(text) -> return confirmPending(text, pending)
                    ConfirmationMatcher.isNo(text) -> return cancelPending(text, pending)
                    else -> pendingActionRepository.updateState(pending.id, PendingActionState.REJECTED)
                }
            }
        }

        val reminderIntent = reminderIntentResolver.resolve(text)
        if (reminderIntent != null) {
            return handleReminderCandidate(text, reminderIntent)
        }

        return handleChat(text, sessionId)
    }

    private suspend fun handleConfirmation(accepted: Boolean): AssistantResult {
        val pending = pendingActionRepository.loadActive()
            ?: return AssistantResult.Failure(
                error = AssistantError.Unexpected("No pending action"),
                userMessage = "Нет действия для подтверждения."
            )
        return if (accepted) confirmPending("", pending) else cancelPending("", pending)
    }

    private suspend fun handleChat(userText: String, sessionId: String): AssistantResult {
        val now = nowProvider()
        val memory = memoryStore.loadConversationContext(sessionId, historyTail)
        val relevantFacts = memoryPipeline.loadRelevantFacts(userText, now)
        val priorityNotes = contextBuilder.currentMessagePriorityNote(userText)
        val messages = contextBuilder.buildChatRequest(memory, userText, relevantFacts, priorityNotes)

        return when (val result = languageModel.generate(LlmRequest(messages))) {
            is LlmResult.Failure -> AssistantResult.Failure(
                error = result.error.toAssistantError(),
                userMessage = "Ошибка ИИ: ${result.error.toUserMessage()}"
            )
            is LlmResult.Success -> {
                val answer = result.value.content
                if (answer.isBlank()) {
                    return AssistantResult.Failure(
                        error = AssistantError.InvalidModelResponse,
                        userMessage = "ИИ вернул пустой ответ. Попробуйте ещё раз."
                    )
                }
                val userMsg = saveUserMessage(sessionId, userText)
                saveAssistantMessage(sessionId, answer)

                val exclude = sessionManager.isExcludedFromExtraction(sessionId)
                val memoryResult = memoryPipeline.processUserTurn(sessionId, userMsg, exclude)

                if (memoryResult.appliedCount > 0) onMemoryChanged()

                if (memoryResult.pendingActionId != null && memoryResult.confirmationPrompt != null) {
                    return AssistantResult.ConfirmationRequired(
                        text = memoryResult.confirmationPrompt,
                        pendingActionId = memoryResult.pendingActionId
                    )
                }

                conversationSummarizer.updateIfNeeded(sessionId)
                AssistantResult.Reply(answer)
            }
        }
    }

    private suspend fun handleReminderCandidate(
        userText: String,
        intent: com.care.voice.brain.reminder.ReminderIntent
    ): AssistantResult {
        val sessionId = sessionManager.currentSessionId()
        val candidate = reminderCoordinator.buildCandidate(intent)
        saveUserMessage(sessionId, userText)
        saveAssistantMessage(sessionId, candidate.answer)

        if (!candidate.requiresConfirmation || candidate.pending == null) {
            return AssistantResult.Reply(candidate.answer)
        }

        val stored = PendingActionCodec.reminderCreate(candidate.pending, nowProvider())
        pendingActionRepository.save(stored)
        return AssistantResult.ConfirmationRequired(candidate.answer, stored.id)
    }

    private suspend fun confirmPending(userText: String, pending: PendingAction): AssistantResult {
        if (PendingActionCodec.isExpired(pending, nowProvider())) {
            pendingActionRepository.updateState(pending.id, PendingActionState.EXPIRED)
            return AssistantResult.Failure(
                error = AssistantError.Unexpected("Expired"),
                userMessage = "Время подтверждения истекло. Повторите запрос."
            )
        }

        val sessionId = sessionManager.currentSessionId()

        return when (pending.type) {
            PendingActionType.CREATE_REMINDER -> {
                val schedule = PendingActionCodec.decodeReminder(pending.payload)
                    ?: return AssistantResult.Failure(AssistantError.InvalidReminder, "Неизвестное действие.")
                pendingActionRepository.updateState(pending.id, PendingActionState.COMPLETED)
                val result = reminderCoordinator.executeConfirmed(schedule, reminderScheduler)
                if (result is AssistantResult.ActionCompleted && userText.isNotBlank()) {
                    saveUserMessage(sessionId, userText)
                    saveAssistantMessage(sessionId, result.text)
                } else if (result is AssistantResult.Failure && userText.isNotBlank()) {
                    pendingActionRepository.updateState(pending.id, PendingActionState.WAITING_CONFIRMATION)
                    saveUserMessage(sessionId, userText)
                    saveAssistantMessage(sessionId, result.userMessage)
                }
                result
            }
            PendingActionType.CONFIRM_MEMORY -> {
                val ok = memoryPipeline.applyConfirmedMemory(pending)
                pendingActionRepository.updateState(
                    pending.id,
                    if (ok) PendingActionState.COMPLETED else PendingActionState.REJECTED
                )
                val reply = if (ok) "Хорошо, запомнила." else "Не удалось сохранить."
                if (userText.isNotBlank()) saveUserMessage(sessionId, userText)
                saveAssistantMessage(sessionId, reply)
                if (ok) onMemoryChanged()
                AssistantResult.ActionCompleted(reply)
            }
            else -> AssistantResult.Failure(AssistantError.InvalidReminder, "Неизвестное действие.")
        }
    }

    private suspend fun cancelPending(userText: String, pending: PendingAction): AssistantResult {
        pendingActionRepository.updateState(pending.id, PendingActionState.REJECTED)
        val sessionId = sessionManager.currentSessionId()

        val answer = when (pending.type) {
            PendingActionType.CREATE_REMINDER -> {
                val schedule = PendingActionCodec.decodeReminder(pending.payload)
                reminderCoordinator.buildCancelled(schedule)
            }
            PendingActionType.CONFIRM_MEMORY -> {
                memoryPipeline.rejectPendingMemory(pending)
                "Хорошо, не буду запоминать."
            }
            else -> "Отменено."
        }

        if (userText.isNotBlank()) saveUserMessage(sessionId, userText)
        saveAssistantMessage(sessionId, answer)
        return AssistantResult.ActionCancelled(answer)
    }

    private suspend fun handleForgetCommands(text: String): AssistantResult? {
        val lower = text.lowercase()
        val now = nowProvider()
        val sessionId = sessionManager.currentSessionId()

        if (lower.contains("не запоминай этот разговор")) {
            sessionManager.markExcludeFromExtraction(sessionId)
            return AssistantResult.Reply("Хорошо, этот разговор не буду использовать для памяти.")
        }

        if (lower.contains("что ты обо мне помнишь") || lower.contains("что ты помнишь")) {
            val facts = memoryPipeline.loadRelevantFacts(text, now)
            val summary = if (facts.isEmpty()) "Я пока ничего важного не запомнила."
            else facts.joinToString("\n") { "- ${it.value}" }
            return AssistantResult.Reply(summary)
        }

        if (lower.contains("забудь")) {
            val valueHint = text.substringAfter("забудь", "").trim(' ', ',', ':', 'ч', 'т', 'о')
            memoryPipeline.forget(
                ForgetMemoryRequest(
                    subject = MemorySubject.User,
                    type = inferForgetType(lower),
                    key = null,
                    valueHint = valueHint.ifBlank { null },
                    reason = "user_forget_command",
                    now = now
                )
            )
            onMemoryChanged()
            return AssistantResult.Reply("Хорошо, забыла.")
        }

        return null
    }

    private fun inferForgetType(lower: String): MemoryType? = when {
        lower.contains("лекар") || lower.contains("аспирин") || lower.contains("таблет") ->
            MemoryType.MEDICATION
        lower.contains("кофе") || lower.contains("люблю") -> MemoryType.PREFERENCE
        else -> null
    }

    private suspend fun saveUserMessage(sessionId: String, text: String): MemoryMessage {
        val msg = MemoryMessage(role = LlmRole.USER, content = text)
        conversationRepository.saveMessage(sessionId, msg)
        return msg
    }

    private suspend fun saveAssistantMessage(sessionId: String, text: String) {
        conversationRepository.saveMessage(sessionId, MemoryMessage(role = LlmRole.ASSISTANT, content = text))
    }
}
