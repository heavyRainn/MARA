package com.care.voice.platform.android.piper

import com.care.voice.brain.speech.SpeechFailureCode

object PiperNativeLibraryLoader {
    private val lock = Any()

    @Volatile
    private var loadState: LoadState = LoadState.NotLoaded

    private sealed interface LoadState {
        data object NotLoaded : LoadState
        data object Loaded : LoadState
        data class Failed(val code: SpeechFailureCode) : LoadState
    }

    fun ensureLoaded(): SpeechFailureCode? = synchronized(lock) {
        when (val state = loadState) {
            LoadState.Loaded -> null
            is LoadState.Failed -> state.code
            LoadState.NotLoaded -> loadInternal()
        }
    }

    private fun loadInternal(): SpeechFailureCode? {
        return try {
            System.loadLibrary("onnxruntime")
            System.loadLibrary("sherpa-onnx-jni")
            loadState = LoadState.Loaded
            null
        } catch (_: UnsatisfiedLinkError) {
            val code = SpeechFailureCode.PIPER_NATIVE_LIBRARY_UNAVAILABLE
            loadState = LoadState.Failed(code)
            code
        } catch (_: Exception) {
            val code = SpeechFailureCode.PIPER_NATIVE_INITIALIZATION_FAILED
            loadState = LoadState.Failed(code)
            code
        }
    }
}
