package com.care.voice.brain.reminder

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ReminderDeliveryCoordinatorTest {

    private val now = 1_700_000_000_000L
    private val reminderId = 42L
    private val text = "Таблетка"

    @Test
    fun notificationSuccessVoiceSpoken() = runTest {
        val speechCalls = AtomicInteger(0)
        val coordinator = coordinator(
            speech = object : ReminderSpeechProvider {
                override suspend fun speak(reminderId: Long, text: String): ReminderSpeechResult {
                    speechCalls.incrementAndGet()
                    return ReminderSpeechResult.Spoken
                }
            }
        )

        val result = coordinator.deliverAfterTriggered(triggeredSnapshot(), now)

        assertTrue(result is CoordinatorDeliveryResult.NotificationAndVoiceComplete)
        assertEquals(1, speechCalls.get())
        assertEquals(VoiceDeliveryStatus.SPOKEN, voiceStore.status)
        assertEquals(ReminderStatus.DELIVERED, persistence.status)
    }

    @Test
    fun notificationSuccessVoiceSkippedByPolicy() = runTest {
        val speechCalls = AtomicInteger(0)
        val coordinator = coordinator(
            settings = FakeSettings(voiceRemindersEnabled = false),
            speech = countingSpeech(speechCalls)
        )

        val result = coordinator.deliverAfterTriggered(triggeredSnapshot(), now)

        assertTrue(result is CoordinatorDeliveryResult.NotificationDeliveredVoiceSkipped)
        assertEquals(0, speechCalls.get())
        assertEquals(VoiceDeliveryStatus.SKIPPED, voiceStore.status)
    }

    @Test
    fun notificationSuccessVoiceFailed() = runTest {
        val coordinator = coordinator(
            speech = object : ReminderSpeechProvider {
                override suspend fun speak(reminderId: Long, text: String) =
                    ReminderSpeechResult.Failed("tts_error")
            }
        )

        val result = coordinator.deliverAfterTriggered(triggeredSnapshot(), now)

        assertTrue(result is CoordinatorDeliveryResult.NotificationDeliveredVoiceFailed)
        assertEquals(VoiceDeliveryStatus.FAILED, voiceStore.status)
        assertEquals(ReminderStatus.DELIVERED, persistence.status)
    }

    @Test
    fun voiceFailureDoesNotRevertDelivered() = runTest {
        val coordinator = coordinator(
            speech = object : ReminderSpeechProvider {
                override suspend fun speak(reminderId: Long, text: String) =
                    ReminderSpeechResult.Failed("timeout")
            }
        )

        coordinator.deliverAfterTriggered(triggeredSnapshot(), now)

        assertEquals(ReminderStatus.DELIVERED, persistence.status)
    }

    @Test
    fun notificationFailureDoesNotInvokeVoice() = runTest {
        val speechCalls = AtomicInteger(0)
        val coordinator = coordinator(
            notification = object : ReminderDeliveryService {
                override suspend fun deliver(reminder: Reminder) =
                    ReminderDeliveryResult.Failed("blocked")
            },
            speech = countingSpeech(speechCalls)
        )

        val result = coordinator.deliverAfterTriggered(triggeredSnapshot(), now)

        assertTrue(result is CoordinatorDeliveryResult.NotificationFailed)
        assertEquals(0, speechCalls.get())
        assertEquals(ReminderStatus.TRIGGERED, persistence.status)
    }

    @Test
    fun duplicateVoiceDeliveryDoesNotSpeakAgain() = runTest {
        val speechCalls = AtomicInteger(0)
        val coordinator = coordinator(speech = countingSpeech(speechCalls))

        coordinator.performVoiceBestEffort(reminderId, text, now)
        coordinator.performVoiceBestEffort(reminderId, text, now + 1)

        assertEquals(1, speechCalls.get())
        assertEquals(VoiceDeliveryStatus.SKIPPED, voiceStore.status)
    }

    @Test
    fun pendingGuardBlocksSecondVoiceAttempt() = runTest {
        val speechCalls = AtomicInteger(0)
        val coordinator = coordinator(speech = countingSpeech(speechCalls))

        assertTrue(voiceStore.tryMarkPending(reminderId, now))
        coordinator.performVoiceBestEffort(reminderId, text, now + 1)

        assertEquals(0, speechCalls.get())
    }

    @Test
    fun quietHoursSkipsVoice() = runTest {
        val speechCalls = AtomicInteger(0)
        val coordinator = coordinator(
            clock = FakeClock(localHour = 23),
            speech = countingSpeech(speechCalls)
        )

        val result = coordinator.performVoiceBestEffort(reminderId, text, now)

        assertTrue(result is CoordinatorDeliveryResult.NotificationDeliveredVoiceSkipped)
        assertEquals(0, speechCalls.get())
        assertEquals(VoiceDeliveryStatus.SKIPPED, voiceStore.status)
    }

    @Test
    fun disabledSkipsVoice() = runTest {
        val speechCalls = AtomicInteger(0)
        val coordinator = coordinator(
            settings = FakeSettings(voiceRemindersEnabled = false),
            speech = countingSpeech(speechCalls)
        )

        coordinator.performVoiceBestEffort(reminderId, text, now)

        assertEquals(0, speechCalls.get())
        assertEquals(VoiceDeliveryStatus.SKIPPED, voiceStore.status)
    }

    @Test
    fun emptyTextSkipsVoice() = runTest {
        val speechCalls = AtomicInteger(0)
        val coordinator = coordinator(speech = countingSpeech(speechCalls))

        coordinator.performVoiceBestEffort(reminderId, "   ", now)

        assertEquals(0, speechCalls.get())
        assertEquals(VoiceDeliveryStatus.SKIPPED, voiceStore.status)
    }

    @Test
    fun reconcileRedeliveryUsesSeparateEntryPoint() = runTest {
        val notificationCalls = AtomicInteger(0)
        val coordinator = coordinator(
            notification = object : ReminderDeliveryService {
                override suspend fun deliver(reminder: Reminder): ReminderDeliveryResult {
                    notificationCalls.incrementAndGet()
                    return ReminderDeliveryResult.Delivered
                }
            }
        )

        val result = coordinator.redeliverFromReconcile(triggeredSnapshot(), now)

        assertTrue(result is CoordinatorDeliveryResult.NotificationAndVoiceComplete)
        assertEquals(1, notificationCalls.get())
    }

    private lateinit var voiceStore: FakeVoiceStateStore
    private lateinit var persistence: FakePersistence

    private fun coordinator(
        notification: ReminderDeliveryService = successNotification(),
        speech: ReminderSpeechProvider = successSpeech(),
        settings: VoiceReminderSettings = FakeSettings(),
        clock: Clock = FakeClock(localHour = 12)
    ): ReminderDeliveryCoordinator {
        voiceStore = FakeVoiceStateStore()
        persistence = FakePersistence(reminderId, text)
        return ReminderDeliveryCoordinator(
            notificationDelivery = notification,
            speechProvider = speech,
            voiceStateStore = voiceStore,
            persistence = persistence,
            policy = VoiceReminderPolicy(settings, clock, FakeCallState()),
            logger = NoOpReminderDeliveryLogger
        )
    }

    private fun triggeredSnapshot() = ReminderDeliverySnapshot(
        id = reminderId,
        text = text,
        status = ReminderStatus.TRIGGERED,
        voiceDeliveryStatus = VoiceDeliveryStatus.NOT_REQUESTED
    )

    private fun successNotification() = object : ReminderDeliveryService {
        override suspend fun deliver(reminder: Reminder) = ReminderDeliveryResult.Delivered
    }

    private fun successSpeech() = object : ReminderSpeechProvider {
        override suspend fun speak(reminderId: Long, text: String) = ReminderSpeechResult.Spoken
    }

    private fun countingSpeech(calls: AtomicInteger) = object : ReminderSpeechProvider {
        override suspend fun speak(reminderId: Long, text: String): ReminderSpeechResult {
            calls.incrementAndGet()
            return ReminderSpeechResult.Spoken
        }
    }
}

private class FakeVoiceStateStore : ReminderVoiceStateStore {
    var status: VoiceDeliveryStatus = VoiceDeliveryStatus.NOT_REQUESTED
        private set

    override suspend fun getStatus(reminderId: Long): VoiceDeliveryStatus = status

    override suspend fun tryMarkPending(reminderId: Long, nowEpochMillis: Long): Boolean {
        if (status != VoiceDeliveryStatus.NOT_REQUESTED) return false
        status = VoiceDeliveryStatus.PENDING
        return true
    }

    override suspend fun markSpoken(reminderId: Long, nowEpochMillis: Long) {
        status = VoiceDeliveryStatus.SPOKEN
    }

    override suspend fun markSkipped(reminderId: Long, reason: VoiceSkipReason, nowEpochMillis: Long) {
        status = VoiceDeliveryStatus.SKIPPED
    }

    override suspend fun markFailed(reminderId: Long, code: String, nowEpochMillis: Long) {
        status = VoiceDeliveryStatus.FAILED
    }

    override suspend fun resetForNewDeliveryCycle(reminderId: Long) {
        status = VoiceDeliveryStatus.NOT_REQUESTED
    }
}

private class FakePersistence(
    private val id: Long,
    private val text: String
) : ReminderDeliveryPersistence {
    var status: ReminderStatus = ReminderStatus.TRIGGERED
        private set

    override suspend fun getSnapshot(reminderId: Long): ReminderDeliverySnapshot? =
        ReminderDeliverySnapshot(id, text, status, VoiceDeliveryStatus.NOT_REQUESTED)

    override suspend fun markNotificationDelivered(reminderId: Long, nowEpochMillis: Long): Boolean {
        status = ReminderStatus.DELIVERED
        return true
    }
}

private class FakeSettings(
    override val voiceRemindersEnabled: Boolean = true,
    override val quietHoursStartHour: Int = 22,
    override val quietHoursEndHour: Int = 8
) : VoiceReminderSettings

private class FakeClock(
    private val localHour: Int
) : Clock {
    override fun currentTimeMillis(): Long = 1_700_000_000_000L
    override fun currentLocalHour(): Int = localHour
}

private class FakeCallState(
    private val active: Boolean = false
) : CallStateProvider {
    override fun isPhoneCallActive(): Boolean = active
}
