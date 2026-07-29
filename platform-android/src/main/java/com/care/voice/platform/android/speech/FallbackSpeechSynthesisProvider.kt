package com.care.voice.platform.android.speech

import com.care.voice.brain.speech.SpeechCancelReason
import com.care.voice.brain.speech.SpeechChunk
import com.care.voice.brain.speech.SpeechFailureCode
import com.care.voice.brain.speech.SpeechFailurePolicy
import com.care.voice.brain.speech.SpeechPlaybackEvent
import com.care.voice.brain.speech.SpeechProviderType
import com.care.voice.brain.speech.SpeechRequest
import com.care.voice.brain.speech.SpeechResult
import com.care.voice.brain.speech.SpeechSettingsProvider
import com.care.voice.brain.speech.SpeechSynthesisProvider
import com.care.voice.platform.android.piper.PiperModelManager
import com.care.voice.platform.android.piper.PiperModelState
import com.care.voice.platform.android.piper.PiperSynthesisResult
import com.care.voice.platform.android.piper.SherpaPiperEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

private const val PIPER_MODEL_READY_TIMEOUT_MS = 45_000L
private const val PIPER_SYNTHESIS_TIMEOUT_MS = 30_000L

class PiperSpeechProvider(
    private val modelManager: PiperModelManager,
    private val engine: SherpaPiperEngine,
    private val pcmPlayer: PcmAudioPlayer,
    private val audioFocus: SpeechAudioFocusManager,
    private val settingsProvider: SpeechSettingsProvider,
) : SpeechSynthesisProvider {
    @Volatile private var activeRequestId: String? = null
    @Volatile private var activeGeneration: Long = 0L
    @Volatile private var cancelled = false

    override suspend fun speak(
        request: SpeechRequest,
        chunks: List<SpeechChunk>,
        generation: Long,
        onEvent: (SpeechPlaybackEvent) -> Unit,
    ): SpeechResult {
        activeRequestId = request.requestId
        activeGeneration = generation
        cancelled = false

        return when (val ready = withTimeoutOrNull(PIPER_MODEL_READY_TIMEOUT_MS) {
            modelManager.ensureReady()
        }) {
            null -> SpeechResult.Failed(SpeechFailureCode.PIPER_TIMEOUT)
            is PiperModelState.Failed -> SpeechResult.Failed(ready.failureCode)
            is PiperModelState.Ready -> speakChunks(request, chunks, generation, ready, onEvent)
            else -> SpeechResult.Failed(SpeechFailureCode.PIPER_MODEL_LOAD_FAILED)
        }
    }

    private suspend fun speakChunks(
        request: SpeechRequest,
        chunks: List<SpeechChunk>,
        generation: Long,
        ready: PiperModelState.Ready,
        onEvent: (SpeechPlaybackEvent) -> Unit,
    ): SpeechResult {
        if (!audioFocus.requestTransientFocus()) {
            return SpeechResult.Failed(SpeechFailureCode.AUDIO_FOCUS_DENIED)
        }

        return try {
            speakChunksInternal(request, chunks, generation, ready, onEvent)
        } finally {
            audioFocus.abandonFocus()
        }
    }

    private suspend fun speakChunksInternal(
        request: SpeechRequest,
        chunks: List<SpeechChunk>,
        generation: Long,
        ready: PiperModelState.Ready,
        onEvent: (SpeechPlaybackEvent) -> Unit,
    ): SpeechResult {
        var spokenCount = 0
        val settings = settingsProvider.current()
        val piperSpeed = settings.resolvedPiperSpeed()
        val speakerId = ready.voice.speakerId
        for (chunk in chunks) {
            if (cancelled || generation != activeGeneration) {
                return SpeechResult.Cancelled(SpeechCancelReason.REQUEST_REPLACED)
            }
            onEvent(
                SpeechPlaybackEvent.ChunkStarted(
                    requestId = request.requestId,
                    chunkId = chunk.chunkId,
                    chunkIndex = chunk.index,
                ),
            )
            modelManager.onSynthesisStarted()
            val synthesis = withTimeoutOrNull(PIPER_SYNTHESIS_TIMEOUT_MS) {
                engine.synthesize(
                    requestId = request.requestId,
                    chunk = chunk,
                    piperSpeed = piperSpeed,
                    speakerId = speakerId,
                )
            } ?: PiperSynthesisResult.Failed(SpeechFailureCode.PIPER_TIMEOUT)
            modelManager.onSynthesisFinished()

            when (synthesis) {
                is PiperSynthesisResult.Success -> {
                    val playbackError = pcmPlayer.play(synthesis.pcm) {
                        cancelled || generation != activeGeneration
                    }
                    if (playbackError != null) {
                        return SpeechResult.Failed(playbackError)
                    }
                    spokenCount++
                    onEvent(
                        SpeechPlaybackEvent.ChunkCompleted(
                            requestId = request.requestId,
                            chunkId = chunk.chunkId,
                            chunkIndex = chunk.index,
                        ),
                    )
                    if (chunk.pauseAfterMs > 0) {
                        awaitCancellablePause(chunk.pauseAfterMs, generation)
                        if (cancelled || generation != activeGeneration) {
                            return SpeechResult.Cancelled(SpeechCancelReason.REQUEST_REPLACED)
                        }
                    }
                }
                is PiperSynthesisResult.Failed -> return SpeechResult.Failed(synthesis.code)
                is PiperSynthesisResult.Cancelled -> return SpeechResult.Cancelled(SpeechCancelReason.REQUEST_REPLACED)
            }
        }

        val spoken = SpeechResult.Spoken(
            provider = SpeechProviderType.PIPER,
            fallbackUsed = false,
            spokenChunkCount = spokenCount,
        )
        onEvent(SpeechPlaybackEvent.Completed(request.requestId, spoken))
        return spoken
    }

    private suspend fun awaitCancellablePause(pauseMs: Int, generation: Long) {
        var remaining = pauseMs.toLong()
        while (remaining > 0) {
            if (cancelled || generation != activeGeneration) return
            val step = min(20L, remaining)
            delay(step)
            remaining -= step
        }
    }

    override suspend fun stop(requestId: String, reason: SpeechCancelReason) {
        if (activeRequestId == requestId || activeRequestId != null) {
            cancelled = true
            engine.cancel(requestId)
        }
    }
}

class FallbackSpeechSynthesisProvider(
    private val piperProvider: PiperSpeechProvider,
    private val androidProvider: AndroidSpeechProvider,
) : SpeechSynthesisProvider {
    @Volatile private var fallbackActive = false

    override suspend fun speak(
        request: SpeechRequest,
        chunks: List<SpeechChunk>,
        generation: Long,
        onEvent: (SpeechPlaybackEvent) -> Unit,
    ): SpeechResult {
        fallbackActive = false
        val piperResult = piperProvider.speak(request, chunks, generation, onEvent)
        if (piperResult is SpeechResult.Spoken && !piperResult.fallbackUsed) {
            return piperResult
        }
        if (piperResult is SpeechResult.Cancelled) return piperResult
        if (piperResult is SpeechResult.Skipped) return piperResult

        val failureCode = when (piperResult) {
            is SpeechResult.Failed -> piperResult.primaryFailure
            else -> SpeechFailureCode.UNKNOWN
        }
        if (!SpeechFailurePolicy.shouldFallback(failureCode)) {
            return piperResult
        }

        fallbackActive = true
        val startIndex = when (piperResult) {
            is SpeechResult.Spoken -> piperResult.spokenChunkCount
            else -> 0
        }
        val remaining = chunks.drop(startIndex)
        if (remaining.isEmpty()) {
            return SpeechResult.Failed(failureCode)
        }

        val androidResult = androidProvider.speak(request, remaining, generation, onEvent)
        return when (androidResult) {
            is SpeechResult.Spoken -> SpeechResult.Spoken(
                provider = SpeechProviderType.ANDROID_TTS,
                fallbackUsed = true,
                fallbackReason = failureCode,
                spokenChunkCount = startIndex + androidResult.spokenChunkCount,
            )
            is SpeechResult.Failed -> SpeechResult.Failed(
                primaryFailure = failureCode,
                fallbackFailure = androidResult.primaryFailure,
            )
            else -> androidResult
        }
    }

    override suspend fun stop(requestId: String, reason: SpeechCancelReason) {
        piperProvider.stop(requestId, reason)
        if (!SpeechFailurePolicy.shouldSkipFallback(reason)) {
            androidProvider.stop(requestId, reason)
        }
    }
}
