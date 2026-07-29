package com.care.voice.brain.speech

interface AssistantSpeechListener {
    fun onSpeechStarted(requestId: String)
    fun onSpeechCompleted(requestId: String, result: SpeechResult)
    fun onSpeechFailed(requestId: String, result: SpeechResult)
    fun onSpeechCancelled(requestId: String, reason: SpeechCancelReason)
}

object NoOpAssistantSpeechListener : AssistantSpeechListener {
    override fun onSpeechStarted(requestId: String) = Unit
    override fun onSpeechCompleted(requestId: String, result: SpeechResult) = Unit
    override fun onSpeechFailed(requestId: String, result: SpeechResult) = Unit
    override fun onSpeechCancelled(requestId: String, reason: SpeechCancelReason) = Unit
}

class AssistantSpeechCoordinator(
    private val playbackCoordinator: SpeechPlaybackCoordinator,
) {
    suspend fun speakAssistantResponse(
        requestId: String,
        text: String,
        listener: AssistantSpeechListener = NoOpAssistantSpeechListener,
    ): SpeechResult {
        val request = SpeechRequest(
            requestId = requestId,
            text = text,
            purpose = SpeechPurpose.ASSISTANT_RESPONSE,
            playbackMode = SpeechPlaybackMode.REPLACE_CURRENT,
        )

        var started = false
        val result = playbackCoordinator.speak(request) { event ->
            when (event) {
                is SpeechPlaybackEvent.Started -> {
                    started = true
                    listener.onSpeechStarted(event.requestId)
                }
                is SpeechPlaybackEvent.Completed -> listener.onSpeechCompleted(event.requestId, event.result)
                is SpeechPlaybackEvent.Failed -> listener.onSpeechFailed(event.requestId, event.result)
                is SpeechPlaybackEvent.Cancelled -> listener.onSpeechCancelled(event.requestId, event.reason)
                else -> Unit
            }
        }

        when (result) {
            is SpeechResult.Spoken -> if (!started) listener.onSpeechStarted(requestId)
            is SpeechResult.Failed -> listener.onSpeechFailed(requestId, result)
            is SpeechResult.Cancelled -> listener.onSpeechCancelled(requestId, result.reason)
            is SpeechResult.Skipped -> listener.onSpeechCompleted(requestId, result)
        }
        if (result is SpeechResult.Spoken && !started) {
            listener.onSpeechCompleted(requestId, result)
        }
        return result
    }

    suspend fun cancel(reason: SpeechCancelReason, requestId: String? = null) {
        playbackCoordinator.cancel(requestId, reason)
    }
}
