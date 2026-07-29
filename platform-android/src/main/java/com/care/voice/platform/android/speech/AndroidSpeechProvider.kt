package com.care.voice.platform.android.speech

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.care.voice.brain.speech.SpeechCancelReason
import com.care.voice.brain.speech.SpeechChunk
import com.care.voice.brain.speech.SpeechFailureCode
import com.care.voice.brain.speech.SpeechPlaybackEvent
import com.care.voice.brain.speech.SpeechProviderType
import com.care.voice.brain.speech.SpeechRequest
import com.care.voice.brain.speech.SpeechResult
import com.care.voice.brain.speech.SpeechSettingsProvider
import com.care.voice.brain.speech.SpeechSynthesisProvider
import com.care.voice.brain.speech.SpeechTuning
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class SharedAndroidTtsSession(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val ruLocale = Locale.forLanguageTag("ru-RU")
    private var tts: TextToSpeech? = null
    @Volatile private var initDone = false
    @Volatile private var initOk = false

    suspend fun ensureReady(speechRate: Float = SpeechTuning.DEFAULT_ANDROID_TTS_SPEECH_RATE): SpeechFailureCode? = suspendCancellableCoroutine { cont ->
        if (initDone) {
            if (initOk) {
                tts?.setSpeechRate(speechRate)
            }
            cont.resume(if (initOk) null else SpeechFailureCode.ANDROID_TTS_INIT_FAILED)
            return@suspendCancellableCoroutine
        }
        val resumed = AtomicBoolean(false)
        fun complete(code: SpeechFailureCode?) {
            if (resumed.compareAndSet(false, true)) cont.resume(code)
        }
        tts = TextToSpeech(appContext) { status ->
            initDone = true
            if (status != TextToSpeech.SUCCESS) {
                initOk = false
                complete(SpeechFailureCode.ANDROID_TTS_INIT_FAILED)
                return@TextToSpeech
            }
            val engine = tts ?: run {
                initOk = false
                complete(SpeechFailureCode.ANDROID_TTS_INIT_FAILED)
                return@TextToSpeech
            }
            val localeResult = engine.setLanguage(ruLocale)
            if (localeResult == TextToSpeech.LANG_MISSING_DATA ||
                localeResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                initOk = false
                complete(SpeechFailureCode.ANDROID_TTS_LANGUAGE_UNAVAILABLE)
                return@TextToSpeech
            }
            selectBestRussianVoice(engine)
            engine.setSpeechRate(speechRate)
            initOk = true
            complete(null)
        }
    }

    fun applySpeechRate(speechRate: Float) {
        tts?.setSpeechRate(speechRate)
    }

    suspend fun speakChunk(
        request: SpeechRequest,
        chunk: SpeechChunk,
        generation: Long,
        activeGeneration: () -> Long,
        timeoutMs: Long = 30_000L,
    ): SpeechResult = suspendCancellableCoroutine { cont ->
        if (generation != activeGeneration()) {
            cont.resume(SpeechResult.Cancelled(SpeechCancelReason.REQUEST_REPLACED))
            return@suspendCancellableCoroutine
        }
        val engine = tts ?: run {
            cont.resume(SpeechResult.Failed(SpeechFailureCode.ANDROID_TTS_INIT_FAILED))
            return@suspendCancellableCoroutine
        }
        val utteranceId = "${request.requestId}-${chunk.chunkId}-$generation"
        val resumed = AtomicBoolean(false)
        fun complete(result: SpeechResult) {
            if (resumed.compareAndSet(false, true)) cont.resume(result)
        }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) {
                if (id == utteranceId) complete(
                    SpeechResult.Spoken(
                        provider = SpeechProviderType.ANDROID_TTS,
                        fallbackUsed = false,
                        spokenChunkCount = chunk.index + 1,
                    ),
                )
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) complete(SpeechResult.Failed(SpeechFailureCode.ANDROID_TTS_SYNTHESIS_FAILED))
            }

            override fun onError(id: String?, errorCode: Int) = onError(id)
        })

        val queued = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            engine.speak(chunk.text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            @Suppress("DEPRECATION")
            engine.speak(chunk.text, TextToSpeech.QUEUE_FLUSH, null)
        }
        if (queued == TextToSpeech.ERROR) {
            complete(SpeechResult.Failed(SpeechFailureCode.ANDROID_TTS_SYNTHESIS_FAILED))
        }
        cont.invokeOnCancellation { engine.stop() }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        initDone = false
        initOk = false
    }

    private fun selectBestRussianVoice(engine: TextToSpeech) {
        val voices = engine.voices ?: return
        val best = voices
            .filter { it.locale.language == ruLocale.language }
            .maxByOrNull { voice ->
                var score = 0
                if (voice.locale.country.equals("RU", ignoreCase = true)) score += 2
                if (!voice.isNetworkConnectionRequired) score += 1
                score
            }
        best?.let { engine.voice = it }
    }
}

class AndroidSpeechProvider(
    private val ttsSession: SharedAndroidTtsSession,
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

        val androidRate = settingsProvider.current().androidTtsSpeechRate
        val initError = ttsSession.ensureReady(androidRate)
        if (initError != null) {
            return SpeechResult.Failed(initError)
        }
        ttsSession.applySpeechRate(androidRate)

        var spokenCount = 0
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
            val chunkResult = withTimeoutOrNull(30_000L) {
                ttsSession.speakChunk(
                    request = request,
                    chunk = chunk,
                    generation = generation,
                    activeGeneration = { activeGeneration },
                )
            } ?: SpeechResult.Failed(SpeechFailureCode.ANDROID_TTS_TIMEOUT)

            when (chunkResult) {
                is SpeechResult.Spoken -> {
                    spokenCount++
                    onEvent(
                        SpeechPlaybackEvent.ChunkCompleted(
                            requestId = request.requestId,
                            chunkId = chunk.chunkId,
                            chunkIndex = chunk.index,
                        ),
                    )
                }
                is SpeechResult.Failed -> return chunkResult
                is SpeechResult.Cancelled -> return chunkResult
                is SpeechResult.Skipped -> return chunkResult
            }
        }

        val spoken = SpeechResult.Spoken(
            provider = SpeechProviderType.ANDROID_TTS,
            fallbackUsed = false,
            spokenChunkCount = spokenCount,
        )
        onEvent(SpeechPlaybackEvent.Completed(request.requestId, spoken))
        return spoken
    }

    override suspend fun stop(requestId: String, reason: SpeechCancelReason) {
        if (activeRequestId == requestId || activeRequestId != null) {
            cancelled = true
            ttsSession.stop()
        }
    }
}
