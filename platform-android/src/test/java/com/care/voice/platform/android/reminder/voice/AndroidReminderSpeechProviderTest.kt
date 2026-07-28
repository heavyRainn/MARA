package com.care.voice.platform.android.reminder.voice

import com.care.voice.brain.reminder.ReminderSpeechResult
import com.care.voice.brain.reminder.VoiceSkipReason
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AndroidReminderSpeechProviderTest {

    private val text = "Владимир. Напоминаю: Таблетка."

    @Test
    fun ttsInitSuccessSpeaksAndShutsDown() = runTest {
        val engine = FakeTtsEngine(initResult = TtsInitResult.Success)
        val focus = FakeAudioFocus(granted = true)
        val provider = AndroidReminderSpeechProvider(engine, focus, speakTimeoutMs = 5_000)

        val result = provider.speak(1L, text)

        assertTrue(result is ReminderSpeechResult.Spoken)
        assertEquals(1, engine.initCalls.get())
        assertEquals(1, engine.speakCalls.get())
        assertEquals(1, engine.stopCalls.get())
        assertEquals(1, engine.shutdownCalls.get())
        assertEquals(1, focus.requestCalls.get())
        assertEquals(1, focus.abandonCalls.get())
    }

    @Test
    fun ttsInitFailureSkipsWithoutSpeak() = runTest {
        val engine = FakeTtsEngine(initResult = TtsInitResult.Unavailable)
        val focus = FakeAudioFocus(granted = true)
        val provider = AndroidReminderSpeechProvider(engine, focus)

        val result = provider.speak(2L, text)

        assertEquals(ReminderSpeechResult.Skipped(VoiceSkipReason.TTS_UNAVAILABLE), result)
        assertEquals(0, engine.speakCalls.get())
        assertEquals(0, focus.requestCalls.get())
    }

    @Test
    fun russianLocaleUnavailableSkips() = runTest {
        val engine = FakeTtsEngine(initResult = TtsInitResult.LocaleUnavailable("ru-RU"))
        val focus = FakeAudioFocus(granted = true)
        val provider = AndroidReminderSpeechProvider(engine, focus)

        val result = provider.speak(3L, text)

        assertEquals(ReminderSpeechResult.Skipped(VoiceSkipReason.TTS_UNAVAILABLE), result)
        assertEquals(0, engine.speakCalls.get())
    }

    @Test
    fun audioFocusDeniedSkipsAndShutsDown() = runTest {
        val engine = FakeTtsEngine(initResult = TtsInitResult.Success)
        val focus = FakeAudioFocus(granted = false)
        val provider = AndroidReminderSpeechProvider(engine, focus)

        val result = provider.speak(4L, text)

        assertEquals(ReminderSpeechResult.Skipped(VoiceSkipReason.AUDIO_FOCUS_DENIED), result)
        assertEquals(1, engine.shutdownCalls.get())
        assertEquals(0, engine.speakCalls.get())
        assertEquals(0, focus.abandonCalls.get())
    }

    @Test
    fun onDoneReturnsSpoken() = runTest {
        val engine = FakeTtsEngine(initResult = TtsInitResult.Success, speakResult = TtsSpeakResult.Done)
        val focus = FakeAudioFocus(granted = true)
        val provider = AndroidReminderSpeechProvider(engine, focus)

        assertTrue(provider.speak(5L, text) is ReminderSpeechResult.Spoken)
    }

    @Test
    fun onErrorReturnsFailed() = runTest {
        val engine = FakeTtsEngine(
            initResult = TtsInitResult.Success,
            speakResult = TtsSpeakResult.Error("engine_error")
        )
        val focus = FakeAudioFocus(granted = true)
        val provider = AndroidReminderSpeechProvider(engine, focus)

        val result = provider.speak(6L, text)

        assertEquals(ReminderSpeechResult.Failed("engine_error"), result)
        assertEquals(1, engine.shutdownCalls.get())
        assertEquals(1, focus.abandonCalls.get())
    }

    @Test
    fun timeoutReturnsFailed() = runTest {
        val engine = FakeTtsEngine(
            initResult = TtsInitResult.Success,
            speakResult = TtsSpeakResult.Timeout,
            speakDelayMs = 50
        )
        val focus = FakeAudioFocus(granted = true)
        val provider = AndroidReminderSpeechProvider(engine, focus, speakTimeoutMs = 10)

        val result = provider.speak(7L, text)

        assertEquals(ReminderSpeechResult.Failed("tts_timeout"), result)
        assertEquals(1, engine.shutdownCalls.get())
        assertEquals(1, focus.abandonCalls.get())
    }

    @Test
    fun shutdownAndAbandonFocusOnAllOutcomes() = runTest {
        val engine = FakeTtsEngine(initResult = TtsInitResult.Success, speakResult = TtsSpeakResult.Done)
        val focus = FakeAudioFocus(granted = true)
        val provider = AndroidReminderSpeechProvider(engine, focus)

        provider.speak(8L, text)

        assertEquals(1, engine.shutdownCalls.get())
        assertEquals(1, focus.abandonCalls.get())
    }
}

private class FakeTtsEngine(
    private val initResult: TtsInitResult,
    private val speakResult: TtsSpeakResult = TtsSpeakResult.Done,
    private val speakDelayMs: Long = 0
) : AndroidTtsEngine {
    val initCalls = AtomicInteger(0)
    val speakCalls = AtomicInteger(0)
    val stopCalls = AtomicInteger(0)
    val shutdownCalls = AtomicInteger(0)

    override suspend fun initialize(): TtsInitResult {
        initCalls.incrementAndGet()
        return initResult
    }

    override suspend fun speak(text: String, utteranceId: String): TtsSpeakResult {
        speakCalls.incrementAndGet()
        if (speakDelayMs > 0) {
            kotlinx.coroutines.delay(speakDelayMs)
        }
        return speakResult
    }

    override fun stop() {
        stopCalls.incrementAndGet()
    }

    override fun shutdown() {
        shutdownCalls.incrementAndGet()
    }
}

private class FakeAudioFocus(
    private val granted: Boolean
) : AudioFocusController {
    val requestCalls = AtomicInteger(0)
    val abandonCalls = AtomicInteger(0)

    override fun requestTransientFocus(): Boolean {
        requestCalls.incrementAndGet()
        return granted
    }

    override fun abandonFocus() {
        abandonCalls.incrementAndGet()
    }
}
