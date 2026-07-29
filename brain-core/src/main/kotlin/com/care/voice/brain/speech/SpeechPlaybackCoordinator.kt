package com.care.voice.brain.speech

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SpeechPlaybackCoordinator(
    private val synthesisProvider: SpeechSynthesisProvider,
    private val settingsProvider: SpeechSettingsProvider = DefaultSpeechSettingsProvider,
    private val normalizer: RussianSpeechTextNormalizer = RussianSpeechTextNormalizer,
    private val chunker: SpeechChunker = SpeechChunker,
) {
    private val mutex = Mutex()
    private var activeRequest: ActiveRequest? = null

    private data class ActiveRequest(
        val request: SpeechRequest,
        val generation: Long,
        @Volatile var cancelled: Boolean = false,
        @Volatile var cancelReason: SpeechCancelReason = SpeechCancelReason.UNKNOWN,
    )

    suspend fun speak(
        request: SpeechRequest,
        onEvent: (SpeechPlaybackEvent) -> Unit = {},
    ): SpeechResult {
        val settings = settingsProvider.current()
        if (!settings.voiceEnabled) {
            return SpeechResult.Skipped(SpeechSkipReason.VOICE_DISABLED)
        }
        when (request.purpose) {
            SpeechPurpose.ASSISTANT_RESPONSE ->
                if (!settings.autoReadAssistantResponses) {
                    return SpeechResult.Skipped(SpeechSkipReason.AUTO_READ_DISABLED)
                }
            SpeechPurpose.REMINDER ->
                if (!settings.readRemindersAloud) {
                    return SpeechResult.Skipped(SpeechSkipReason.REMINDER_VOICE_DISABLED)
                }
            SpeechPurpose.SYSTEM_MESSAGE -> Unit
        }

        val normalized = normalizer.normalize(request.text)
        if (normalized.isBlank()) {
            return SpeechResult.Skipped(SpeechSkipReason.EMPTY_TEXT)
        }

        val chunks = if (request.purpose == SpeechPurpose.REMINDER) {
            listOf(
                SpeechChunk(
                    chunkId = "${request.requestId}-chunk-0",
                    index = 0,
                    text = normalized,
                    pauseAfterMs = 0,
                ),
            )
        } else {
            chunker.chunk(
                text = normalized,
                requestId = request.requestId,
                chunkPauseMs = settings.speechChunkPauseMs,
                paragraphPauseMs = settings.speechParagraphPauseMs,
            )
        }
        if (chunks.isEmpty()) {
            return SpeechResult.Skipped(SpeechSkipReason.EMPTY_TEXT)
        }

        when (request.playbackMode) {
            SpeechPlaybackMode.REPLACE_CURRENT ->
                cancel(null, SpeechCancelReason.REQUEST_REPLACED)
            SpeechPlaybackMode.QUEUE ->
                waitForIdle(settings.reminderQueueTimeoutMs)
        }

        val generation = System.nanoTime()
        val normalizedRequest = request.copy(text = normalized)

        val active = mutex.withLock {
            if (request.purpose == SpeechPurpose.REMINDER && activeRequest != null) {
                return SpeechResult.Skipped(SpeechSkipReason.REMINDER_BUSY_TIMEOUT)
            }
            if (request.playbackMode == SpeechPlaybackMode.REPLACE_CURRENT && activeRequest != null) {
                cancelActiveLocked(SpeechCancelReason.REQUEST_REPLACED)
            }
            ActiveRequest(normalizedRequest, generation).also { activeRequest = it }
        }

        onEvent(SpeechPlaybackEvent.Preparing(request.requestId))
        onEvent(SpeechPlaybackEvent.Started(request.requestId))

        val result = try {
            synthesisProvider.speak(
                request = normalizedRequest,
                chunks = chunks,
                generation = generation,
            ) { event ->
                if (!isStale(active, event.requestId)) {
                    onEvent(event)
                }
            }
        } finally {
            mutex.withLock {
                if (activeRequest?.generation == generation) {
                    activeRequest = null
                }
            }
        }

        if (active.cancelled && result !is SpeechResult.Cancelled) {
            return SpeechResult.Cancelled(active.cancelReason)
        }
        return result
    }

    suspend fun cancel(requestId: String?, reason: SpeechCancelReason): Boolean {
        val stopTarget = mutex.withLock {
            val active = activeRequest ?: return false
            if (requestId != null && active.request.requestId != requestId) return false
            active.cancelled = true
            active.cancelReason = reason
            active.request.requestId
        }
        synthesisProvider.stop(stopTarget, reason)
        return true
    }

    fun currentRequestId(): String? = activeRequest?.request?.requestId

    private suspend fun waitForIdle(timeoutMs: Long) {
        if (timeoutMs <= 0L) return
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val idle = mutex.withLock { activeRequest == null }
            if (idle) return
            delay(100L)
        }
    }

    private suspend fun cancelActiveLocked(reason: SpeechCancelReason) {
        val active = activeRequest ?: return
        active.cancelled = true
        active.cancelReason = reason
        val requestId = active.request.requestId
        activeRequest = null
        synthesisProvider.stop(requestId, reason)
    }

    private fun isStale(active: ActiveRequest, requestId: String): Boolean =
        active.cancelled || active.request.requestId != requestId
}
