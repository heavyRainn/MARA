package com.care.voice.platform.tts

import com.care.voice.brain.speech.AssistantSpeechCoordinator
import com.care.voice.brain.speech.SpeechCancelReason
import com.care.voice.brain.speech.SpeechResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TtsCallbacks(
    val onStart: (() -> Unit)? = null,
    val onDone: (() -> Unit)? = null,
    val onError: (() -> Unit)? = null,
)

/**
 * Facade over [AssistantSpeechCoordinator] for legacy call sites.
 */
class TtsManager(
    private val assistantSpeechCoordinator: AssistantSpeechCoordinator,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    @Volatile
    private var activeRequestId: String? = null

    fun speak(
        text: String,
        utteranceId: String,
        callbacks: TtsCallbacks = TtsCallbacks(),
    ) {
        scope.launch {
            activeRequestId?.let { previous ->
                assistantSpeechCoordinator.cancel(SpeechCancelReason.REQUEST_REPLACED, previous)
            }
            activeRequestId = utteranceId
            try {
                val result = assistantSpeechCoordinator.speakAssistantResponse(
                    requestId = utteranceId,
                    text = text,
                    listener = object : com.care.voice.brain.speech.AssistantSpeechListener {
                        override fun onSpeechStarted(requestId: String) {
                            if (activeRequestId != requestId) return
                            callbacks.onStart?.invoke()
                        }

                        override fun onSpeechCompleted(requestId: String, result: SpeechResult) {
                            if (activeRequestId != requestId) return
                            activeRequestId = null
                            callbacks.onDone?.invoke()
                        }

                        override fun onSpeechFailed(requestId: String, result: SpeechResult) {
                            if (activeRequestId != requestId) return
                            activeRequestId = null
                            callbacks.onError?.invoke()
                        }

                        override fun onSpeechCancelled(requestId: String, reason: SpeechCancelReason) {
                            if (activeRequestId != requestId) return
                            activeRequestId = null
                            callbacks.onError?.invoke()
                        }
                    },
                )
                if (activeRequestId != utteranceId) return@launch
                activeRequestId = null
                when (result) {
                    is SpeechResult.Spoken, is SpeechResult.Skipped -> callbacks.onDone?.invoke()
                    is SpeechResult.Failed, is SpeechResult.Cancelled -> callbacks.onError?.invoke()
                }
            } catch (t: Throwable) {
                if (activeRequestId == utteranceId) {
                    activeRequestId = null
                    callbacks.onError?.invoke()
                }
            }
        }
    }

    fun stop() {
        val requestId = activeRequestId ?: return
        scope.launch {
            assistantSpeechCoordinator.cancel(SpeechCancelReason.USER_STOPPED_PLAYBACK, requestId)
            activeRequestId = null
        }
    }

    fun shutdown() {
        scope.launch {
            assistantSpeechCoordinator.cancel(SpeechCancelReason.OWNER_DESTROYED, activeRequestId)
            activeRequestId = null
        }
    }
}
