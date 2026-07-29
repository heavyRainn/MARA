package com.care.voice.brain.speech

enum class SpeechPurpose {
    ASSISTANT_RESPONSE,
    REMINDER,
    SYSTEM_MESSAGE,
}

enum class SpeechPlaybackMode {
    REPLACE_CURRENT,
    QUEUE,
}

enum class SpeechProviderType {
    PIPER,
    ANDROID_TTS,
}

enum class SpeechCancelReason {
    USER_STARTED_LISTENING,
    NEW_ASSISTANT_RESPONSE,
    USER_STOPPED_PLAYBACK,
    PHONE_CALL,
    AUDIO_FOCUS_LOSS,
    OWNER_DESTROYED,
    REQUEST_REPLACED,
    TIMEOUT,
    UNKNOWN,
}

enum class SpeechSkipReason {
    EMPTY_TEXT,
    VOICE_DISABLED,
    AUTO_READ_DISABLED,
    REMINDER_VOICE_DISABLED,
    REMINDER_QUIET_HOURS,
    PHONE_CALL_ACTIVE,
    REMINDER_BUSY_TIMEOUT,
    STALE_REQUEST,
    CANCELLED,
}

enum class SpeechFailureCode {
    PIPER_NATIVE_LIBRARY_UNAVAILABLE,
    PIPER_NATIVE_INITIALIZATION_FAILED,
    PIPER_MODEL_NOT_FOUND,
    PIPER_MODEL_INSTALL_FAILED,
    PIPER_MODEL_CHECKSUM_FAILED,
    PIPER_MODEL_CONFIG_INVALID,
    PIPER_MODEL_LOAD_FAILED,
    PIPER_PHONEMIZATION_FAILED,
    PIPER_INFERENCE_FAILED,
    PIPER_INVALID_AUDIO,
    PIPER_TIMEOUT,
    AUDIO_FOCUS_DENIED,
    AUDIO_TRACK_INIT_FAILED,
    AUDIO_PLAYBACK_FAILED,
    ANDROID_TTS_INIT_FAILED,
    ANDROID_TTS_LANGUAGE_UNAVAILABLE,
    ANDROID_TTS_SYNTHESIS_FAILED,
    ANDROID_TTS_TIMEOUT,
    UNKNOWN,
}

data class SpeechRequest(
    val requestId: String,
    val text: String,
    val locale: String = "ru-RU",
    val purpose: SpeechPurpose,
    val playbackMode: SpeechPlaybackMode = SpeechPlaybackMode.REPLACE_CURRENT,
)

data class SpeechChunk(
    val chunkId: String,
    val index: Int,
    val text: String,
    val pauseAfterMs: Int = 0,
)

object SpeechTuning {
    const val PIPER_SPEED_MIN = 0.85f
    const val PIPER_SPEED_MAX = 1.35f
    const val DEFAULT_PIPER_SPEED = 1.12f
    const val DEFAULT_ANDROID_TTS_SPEECH_RATE = 0.9f
    const val DEFAULT_CHUNK_PAUSE_MS = 110
    const val DEFAULT_PARAGRAPH_PAUSE_MS = 220
    const val DEFAULT_PREFERRED_VOICE_ID = "ru_RU-irina-medium"

    fun safePiperSpeed(value: Float): Float = value.coerceIn(PIPER_SPEED_MIN, PIPER_SPEED_MAX)
}

data class SpeechSettings(
    val voiceEnabled: Boolean = true,
    val autoReadAssistantResponses: Boolean = true,
    val readRemindersAloud: Boolean = true,
    val preferredProvider: SpeechProviderType = SpeechProviderType.PIPER,
    val preferredVoiceId: String = SpeechTuning.DEFAULT_PREFERRED_VOICE_ID,
    val piperSpeed: Float = SpeechTuning.DEFAULT_PIPER_SPEED,
    val androidTtsSpeechRate: Float = SpeechTuning.DEFAULT_ANDROID_TTS_SPEECH_RATE,
    val speechChunkPauseMs: Int = SpeechTuning.DEFAULT_CHUNK_PAUSE_MS,
    val speechParagraphPauseMs: Int = SpeechTuning.DEFAULT_PARAGRAPH_PAUSE_MS,
    val piperModelIdleTimeoutMs: Long = 300_000L,
    val reminderQueueTimeoutMs: Long = 60_000L,
) {
    fun resolvedPiperSpeed(): Float = SpeechTuning.safePiperSpeed(piperSpeed)
}

sealed interface SpeechResult {
    data class Spoken(
        val provider: SpeechProviderType,
        val fallbackUsed: Boolean,
        val fallbackReason: SpeechFailureCode? = null,
        val spokenChunkCount: Int,
    ) : SpeechResult

    data class Skipped(
        val reason: SpeechSkipReason,
    ) : SpeechResult

    data class Failed(
        val primaryFailure: SpeechFailureCode,
        val fallbackFailure: SpeechFailureCode? = null,
    ) : SpeechResult

    data class Cancelled(
        val reason: SpeechCancelReason,
    ) : SpeechResult
}

sealed interface SpeechPlaybackEvent {
    val requestId: String

    data class Preparing(override val requestId: String) : SpeechPlaybackEvent
    data class Started(override val requestId: String) : SpeechPlaybackEvent
    data class ChunkStarted(
        override val requestId: String,
        val chunkId: String,
        val chunkIndex: Int,
    ) : SpeechPlaybackEvent

    data class ChunkCompleted(
        override val requestId: String,
        val chunkId: String,
        val chunkIndex: Int,
    ) : SpeechPlaybackEvent

    data class Completed(
        override val requestId: String,
        val result: SpeechResult.Spoken,
    ) : SpeechPlaybackEvent

    data class Failed(
        override val requestId: String,
        val result: SpeechResult.Failed,
    ) : SpeechPlaybackEvent

    data class Cancelled(
        override val requestId: String,
        val reason: SpeechCancelReason,
    ) : SpeechPlaybackEvent
}

interface SpeechSettingsProvider {
    fun current(): SpeechSettings
}

object DefaultSpeechSettingsProvider : SpeechSettingsProvider {
    override fun current(): SpeechSettings = SpeechSettings()
}

interface SpeechSynthesisProvider {
    suspend fun speak(
        request: SpeechRequest,
        chunks: List<SpeechChunk>,
        generation: Long,
        onEvent: (SpeechPlaybackEvent) -> Unit,
    ): SpeechResult

    suspend fun stop(requestId: String, reason: SpeechCancelReason)
}
