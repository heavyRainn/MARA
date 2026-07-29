package com.care.voice.platform.android.speech

import android.content.Context
import com.care.voice.brain.speech.AssistantSpeechCoordinator
import com.care.voice.brain.speech.DefaultSpeechSettingsProvider
import com.care.voice.brain.speech.ReminderSpeechCoordinator
import com.care.voice.brain.speech.SpeechPlaybackCoordinator
import com.care.voice.brain.speech.SpeechSettingsProvider
import com.care.voice.platform.android.piper.PiperModelManager
import com.care.voice.platform.android.piper.PiperVoicePackInstaller
import com.care.voice.platform.android.piper.SherpaPiperEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object YasnaSpeechHolder {
    @Volatile
    private var graph: YasnaSpeechGraph? = null

    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun get(context: Context): YasnaSpeechGraph {
        val appContext = context.applicationContext
        return graph ?: synchronized(this) {
            graph ?: buildGraph(appContext).also { graph = it }
        }
    }

    fun preloadVoiceModel(context: Context) {
        preloadScope.launch {
            runCatching { get(context).warmUpPiperModel() }
        }
    }

    private fun buildGraph(context: Context): YasnaSpeechGraph {
        val settingsProvider: SpeechSettingsProvider = DefaultSpeechSettingsProvider
        val installer = PiperVoicePackInstaller(context)
        val piperEngine = SherpaPiperEngine()
        val modelManager = PiperModelManager(installer, piperEngine, settingsProvider)
        val pcmPlayer = PcmAudioPlayer()
        val audioFocus = SpeechAudioFocusManager(context)
        val ttsSession = SharedAndroidTtsSession(context)

        val piperProvider = PiperSpeechProvider(
            modelManager = modelManager,
            engine = piperEngine,
            pcmPlayer = pcmPlayer,
            audioFocus = audioFocus,
            settingsProvider = settingsProvider,
        )
        val androidProvider = AndroidSpeechProvider(ttsSession, settingsProvider)
        val fallbackProvider = FallbackSpeechSynthesisProvider(piperProvider, androidProvider)
        val playbackCoordinator = SpeechPlaybackCoordinator(
            synthesisProvider = fallbackProvider,
            settingsProvider = settingsProvider,
        )
        val assistantCoordinator = AssistantSpeechCoordinator(playbackCoordinator)
        val reminderCoordinator = ReminderSpeechCoordinator(playbackCoordinator)

        return YasnaSpeechGraph(
            assistantCoordinator = assistantCoordinator,
            reminderCoordinator = reminderCoordinator,
            playbackCoordinator = playbackCoordinator,
            ttsSession = ttsSession,
            modelManager = modelManager,
        )
    }
}

data class YasnaSpeechGraph(
    val assistantCoordinator: AssistantSpeechCoordinator,
    val reminderCoordinator: ReminderSpeechCoordinator,
    val playbackCoordinator: SpeechPlaybackCoordinator,
    val ttsSession: SharedAndroidTtsSession,
    private val modelManager: PiperModelManager,
) {
    suspend fun warmUpPiperModel() {
        modelManager.ensureReady()
    }
}
