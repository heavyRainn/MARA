package com.care.voice.platform.android.piper

import com.care.voice.brain.speech.SpeechChunk
import com.care.voice.brain.speech.SpeechFailureCode
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

data class PcmAudio(
    val samples: ShortArray,
    val sampleRateHz: Int,
    val channelCount: Int = 1,
)

sealed interface PiperLoadResult {
    data class Ready(val sampleRateHz: Int) : PiperLoadResult
    data class Failed(val code: SpeechFailureCode) : PiperLoadResult
}

sealed interface PiperSynthesisResult {
    data class Success(
        val pcm: PcmAudio,
        val synthesisDurationMs: Long,
    ) : PiperSynthesisResult

    data class Failed(val code: SpeechFailureCode) : PiperSynthesisResult
    data class Cancelled(val requestId: String) : PiperSynthesisResult
}

class SherpaPiperEngine {
    private var tts: OfflineTts? = null
    @Volatile private var cancelledRequestId: String? = null

    suspend fun load(installed: PiperInstalledVoice): PiperLoadResult = withContext(Dispatchers.IO) {
        PiperNativeLibraryLoader.ensureLoaded()?.let { return@withContext PiperLoadResult.Failed(it) }
        unloadInternal()
        val modelFile = File(installed.modelPath)
        if (!modelFile.isFile) {
            return@withContext PiperLoadResult.Failed(SpeechFailureCode.PIPER_MODEL_NOT_FOUND)
        }
        val voiceDir = modelFile.parentFile ?: return@withContext PiperLoadResult.Failed(SpeechFailureCode.PIPER_MODEL_CONFIG_INVALID)
        val tokens = File(installed.tokensPath)
        if (!tokens.isFile) {
            return@withContext PiperLoadResult.Failed(SpeechFailureCode.PIPER_MODEL_CONFIG_INVALID)
        }
        return@withContext try {
            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = installed.modelPath,
                        tokens = tokens.absolutePath,
                        dataDir = installed.espeakDataPath.orEmpty(),
                    ),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                ),
            )
            tts = OfflineTts(assetManager = null, config = config)
            PiperLoadResult.Ready(tts?.sampleRate() ?: installed.sampleRateHz)
        } catch (_: Exception) {
            PiperLoadResult.Failed(SpeechFailureCode.PIPER_MODEL_LOAD_FAILED)
        }
    }

    suspend fun synthesize(
        requestId: String,
        chunk: SpeechChunk,
        piperSpeed: Float,
        speakerId: Int = 0,
    ): PiperSynthesisResult = withContext(Dispatchers.IO) {
        val engine = tts ?: return@withContext PiperSynthesisResult.Failed(SpeechFailureCode.PIPER_MODEL_LOAD_FAILED)
        if (chunk.text.isBlank()) {
            return@withContext PiperSynthesisResult.Failed(SpeechFailureCode.PIPER_PHONEMIZATION_FAILED)
        }
        cancelledRequestId = null
        val started = System.currentTimeMillis()
        return@withContext try {
            val audio = engine.generateWithConfig(
                text = chunk.text,
                config = GenerationConfig(sid = speakerId, speed = piperSpeed),
            )
            if (cancelledRequestId == requestId) {
                return@withContext PiperSynthesisResult.Cancelled(requestId)
            }
            if (audio.samples.isEmpty()) {
                return@withContext PiperSynthesisResult.Failed(SpeechFailureCode.PIPER_INVALID_AUDIO)
            }
            val pcm = floatToPcm16(audio.samples)
            PiperSynthesisResult.Success(
                pcm = PcmAudio(pcm, audio.sampleRate),
                synthesisDurationMs = System.currentTimeMillis() - started,
            )
        } catch (_: Exception) {
            PiperSynthesisResult.Failed(SpeechFailureCode.PIPER_INFERENCE_FAILED)
        }
    }

    fun cancel(requestId: String) {
        cancelledRequestId = requestId
    }

    suspend fun unload() = withContext(Dispatchers.IO) {
        unloadInternal()
    }

    fun sampleRateHz(): Int = tts?.sampleRate() ?: 22_050

    private fun unloadInternal() {
        tts?.free()
        tts = null
    }

    private fun floatToPcm16(samples: FloatArray): ShortArray {
        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            val clipped = min(1.0f, max(-1.0f, samples[i]))
            out[i] = (clipped * 32767.0f).toInt().toShort()
        }
        return out
    }
}
