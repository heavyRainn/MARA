package com.care.voice.brain.memory.pipeline

import com.care.voice.brain.memory.ConversationRepository
import com.care.voice.brain.memory.MemoryMessage
import com.care.voice.brain.memory.MemoryRepository
import com.care.voice.brain.memory.SummaryRepository
import com.care.voice.brain.memory.extract.MemoryExtractor
import com.care.voice.brain.memory.fact.ForgetMemoryRequest
import com.care.voice.brain.memory.fact.MemoryCandidate
import com.care.voice.brain.memory.fact.MemoryQuery
import com.care.voice.brain.memory.fact.MemorySubject
import com.care.voice.brain.memory.fact.MemoryType
import com.care.voice.brain.memory.policy.MemoryPolicy
import com.care.voice.brain.memory.policy.PolicyDecision
import com.care.voice.brain.memory.resolve.MemoryConflictResolver
import com.care.voice.brain.memory.retrieve.MemoryRetriever
import com.care.voice.brain.pending.PendingAction
import com.care.voice.brain.pending.PendingActionCodec
import com.care.voice.brain.pending.PendingActionRepository
import com.care.voice.brain.pending.PendingActionState
import com.care.voice.brain.pending.PendingActionType
import com.care.voice.brain.profile.UserProfileProjector
import com.care.voice.brain.memory.UserProfile
import com.care.voice.brain.memory.MemoryStore
import com.care.voice.brain.util.JsonExtractor
import java.time.Instant

/**
 * Extract → Validate (Policy) → Resolve → Confirm → Store
 */
class MemoryPipeline(
    private val extractor: MemoryExtractor,
    private val memoryRepository: MemoryRepository,
    private val conversationRepository: ConversationRepository,
    private val summaryRepository: SummaryRepository,
    private val memoryStore: MemoryStore,
    private val pendingActionRepository: PendingActionRepository,
    private val policy: MemoryPolicy = MemoryPolicy(),
    private val conflictResolver: MemoryConflictResolver = MemoryConflictResolver(),
    private val retriever: MemoryRetriever = MemoryRetriever(),
    private val nowProvider: () -> Instant = { Instant.now() }
) {

    data class ProcessResult(
        val confirmationPrompt: String? = null,
        val pendingActionId: String? = null,
        val appliedCount: Int = 0
    )

    suspend fun processUserTurn(
        sessionId: String,
        userMessage: MemoryMessage,
        excludeExtraction: Boolean
    ): ProcessResult {
        if (excludeExtraction) return ProcessResult()

        val recent = conversationRepository.loadTail(sessionId, 4)
        val candidates = runCatching { extractor.extract(userMessage, recent) }.getOrDefault(emptyList())
        if (candidates.isEmpty()) return ProcessResult()

        var applied = 0
        var pendingPrompt: String? = null
        var pendingId: String? = null
        val now = nowProvider()

        for (candidate in candidates) {
            when (policy.evaluate(candidate)) {
                PolicyDecision.REJECT -> continue
                PolicyDecision.REQUIRE_CONFIRMATION -> {
                    val prompt = buildConfirmationPrompt(candidate)
                    val action = PendingActionCodec.memoryConfirm(serializeCandidate(candidate), now)
                    pendingActionRepository.save(action)
                    pendingPrompt = prompt
                    pendingId = action.id
                    break
                }
                PolicyDecision.AUTO_APPLY -> {
                    if (applyCandidate(candidate, userMessage.id, now, confirmed = false)) applied++
                }
            }
        }

        if (applied > 0) rebuildProfileProjection()
        return ProcessResult(pendingPrompt, pendingId, applied)
    }

    suspend fun applyConfirmedMemory(pending: PendingAction): Boolean {
        if (pending.type != PendingActionType.CONFIRM_MEMORY) return false
        val candidate = deserializeCandidate(pending.payload) ?: return false
        val now = nowProvider()
        val messageId = JsonExtractor.stringField(pending.payload, "messageId") ?: "confirmed"
        val ok = applyCandidate(candidate, messageId, now, confirmed = true)
        if (ok) rebuildProfileProjection()
        pendingActionRepository.updateState(pending.id, PendingActionState.COMPLETED)
        return ok
    }

    suspend fun rejectPendingMemory(pending: PendingAction) {
        pendingActionRepository.updateState(pending.id, PendingActionState.REJECTED)
    }

    suspend fun forget(request: ForgetMemoryRequest) {
        memoryRepository.forget(request)
        rebuildProfileProjection()
    }

    suspend fun loadRelevantFacts(userText: String, now: Instant) =
        memoryRepository.findRelevant(
            MemoryQuery(
                userText = userText,
                topicHint = retriever.classifyTopic(userText),
                now = now
            )
        ).let { retriever.rank(it, MemoryQuery(userText, retriever.classifyTopic(userText), now = now)) }

    private suspend fun applyCandidate(
        candidate: MemoryCandidate,
        messageId: String,
        now: Instant,
        confirmed: Boolean
    ): Boolean {
        val existing = memoryRepository.findActiveByKey(candidate.subject, candidate.type, candidate.key)
        if (memoryRepository.hasTombstone(candidate.subject, candidate.type, candidate.key, candidate.value?.hashCode()?.toString())) {
            if (candidate.operation != com.care.voice.brain.memory.fact.MemoryOperation.ADD) return false
        }
        val result = conflictResolver.resolve(candidate, existing, messageId, now, confirmed)
        return when (result) {
            is MemoryConflictResolver.ResolveResult.NoChange -> false
            is MemoryConflictResolver.ResolveResult.Apply -> {
                memoryRepository.applyMutation(result.mutation)
                true
            }
        }
    }

    private suspend fun rebuildProfileProjection() {
        val all = memoryRepository.findRelevant(MemoryQuery("", now = nowProvider()))
        val profile = UserProfileProjector.project(all)
        memoryStore.saveUserProfile(profile)
    }

    private fun buildConfirmationPrompt(candidate: MemoryCandidate): String = when (candidate.operation) {
        com.care.voice.brain.memory.fact.MemoryOperation.DELETE ->
            "Я услышала, что нужно забыть: ${candidate.value ?: candidate.key}. Запомнить это изменение?"
        else -> "Я услышала: ${candidate.value}. Запомнить это?"
    }

    private fun serializeCandidate(candidate: MemoryCandidate): String =
        """{"messageId":"","operation":"${candidate.operation.name}","type":"${candidate.type.name}","key":${json(candidate.key)},"value":${json(candidate.value)},"confidence":${candidate.confidence},"subjectType":"${subjectType(candidate.subject)}","relation":${json((candidate.subject as? MemorySubject.RelatedPerson)?.relation)},"subjectName":${json((candidate.subject as? MemorySubject.RelatedPerson)?.name)},"sensitivity":"${candidate.sensitivity.name}","requiresConfirmation":true,"reason":${json(candidate.reason)}}"""

    private fun deserializeCandidate(payload: String): MemoryCandidate? {
        val json = JsonExtractor.extractObject(payload) ?: payload
        return com.care.voice.brain.memory.extract.MemoryCandidateParser.parse("[$json]", nowProvider()).firstOrNull()
    }

    private fun subjectType(subject: MemorySubject): String = when (subject) {
        MemorySubject.User -> "USER"
        is MemorySubject.RelatedPerson -> "RELATED_PERSON"
        MemorySubject.Unknown -> "UNKNOWN"
    }

    private fun json(value: String?): String =
        if (value == null) "null" else "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
