package com.care.voice.platform.android.piper

import com.care.voice.brain.speech.SpeechFailureCode
import com.care.voice.brain.speech.SpeechSettingsProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface PiperModelState {
    data object Uninitialized : PiperModelState
    data object Installing : PiperModelState
    data object Loading : PiperModelState
    data class Ready(
        val voice: PiperInstalledVoice,
        val loadedAtMillis: Long,
    ) : PiperModelState

    data class Failed(
        val failureCode: SpeechFailureCode,
        val retryAllowed: Boolean,
    ) : PiperModelState
}

class PiperModelManager(
    private val installer: PiperVoicePackInstaller,
    private val engine: SherpaPiperEngine,
    private val settingsProvider: SpeechSettingsProvider,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutex = Mutex()
    private var state: PiperModelState = PiperModelState.Uninitialized
    private var inflight: CompletableDeferred<PiperModelState>? = null
    private var idleJob: Job? = null
    @Volatile private var activeSynthesisCount = 0

    suspend fun ensureReady(): PiperModelState = mutex.withLock {
        when (val current = state) {
            is PiperModelState.Ready -> {
                scheduleIdleUnload()
                current
            }
            is PiperModelState.Failed -> if (!current.retryAllowed) current else loadLocked()
            PiperModelState.Uninitialized,
            PiperModelState.Installing,
            PiperModelState.Loading,
            -> loadLocked()
        }
    }

    fun onSynthesisStarted() {
        activeSynthesisCount++
        idleJob?.cancel()
    }

    fun onSynthesisFinished() {
        activeSynthesisCount = (activeSynthesisCount - 1).coerceAtLeast(0)
        if (activeSynthesisCount == 0) scheduleIdleUnload()
    }

    suspend fun unloadIfIdle() = mutex.withLock {
        if (activeSynthesisCount > 0) return
        engine.unload()
        state = PiperModelState.Uninitialized
    }

    private suspend fun loadLocked(): PiperModelState {
        inflight?.let { return it.await() }

        val deferred = CompletableDeferred<PiperModelState>()
        inflight = deferred
        state = PiperModelState.Installing

        val result = withContext(Dispatchers.IO) {
            val installed = installer.ensureInstalled().getOrElse {
                return@withContext PiperModelState.Failed(
                    SpeechFailureCode.PIPER_MODEL_INSTALL_FAILED,
                    retryAllowed = true,
                )
            }
            state = PiperModelState.Loading
            when (val load = engine.load(installed)) {
                is PiperLoadResult.Ready -> PiperModelState.Ready(installed, System.currentTimeMillis())
                is PiperLoadResult.Failed -> PiperModelState.Failed(load.code, retryAllowed = true)
            }
        }

        state = result
        deferred.complete(result)
        inflight = null
        if (result is PiperModelState.Ready) scheduleIdleUnload()
        return result
    }

    private fun scheduleIdleUnload() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(settingsProvider.current().piperModelIdleTimeoutMs)
            if (activeSynthesisCount == 0) unloadIfIdle()
        }
    }
}
