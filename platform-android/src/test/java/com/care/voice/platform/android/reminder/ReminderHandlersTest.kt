package com.care.voice.platform.android.reminder

import com.care.voice.brain.reminder.CoordinatorDeliveryResult
import com.care.voice.brain.reminder.NoOpReminderDeliveryLogger
import com.care.voice.brain.reminder.Reminder
import com.care.voice.brain.reminder.ReminderDeliveryCoordinator
import com.care.voice.brain.reminder.ReminderDeliveryMode
import com.care.voice.brain.reminder.ReminderDeliveryPersistence
import com.care.voice.brain.reminder.ReminderDeliveryResult
import com.care.voice.brain.reminder.ReminderDeliveryService
import com.care.voice.brain.reminder.ReminderDeliverySnapshot
import com.care.voice.brain.reminder.ReminderPrecision
import com.care.voice.brain.reminder.ReminderSpeechProvider
import com.care.voice.brain.reminder.ReminderSpeechResult
import com.care.voice.brain.reminder.ReminderStatus
import com.care.voice.brain.reminder.ReminderVoiceStateStore
import com.care.voice.brain.reminder.VoiceDeliveryStatus
import com.care.voice.brain.reminder.VoiceReminderPolicy
import com.care.voice.brain.reminder.VoiceSkipReason
import com.care.voice.data.history.ReminderEntity
import com.care.voice.data.reminder.AlarmScheduleOutcome
import com.care.voice.data.repository.ReminderDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ReminderHandlersTest {

    private val now = 1_700_000_000_000L

    @Test
    fun duplicateAlarmOnTriggeredIgnoredButReconcileRedelivers() = runTest {
        val dao = HandlerFakeDao()
        val notificationCalls = AtomicInteger(0)
        val id = dao.insert(
            scheduledEntity().copy(status = ReminderStatus.TRIGGERED, triggeredAt = now)
        )
        val coordinator = testCoordinator(dao, notificationCalls)

        val alarmResult = ReminderTriggerHandler.handle(
            entity = dao.getById(id)!!,
            dao = dao,
            coordinator = coordinator,
            now = now + 1
        )
        assertTrue(alarmResult is TriggerHandleResult.SkippedIdempotent)
        assertEquals(0, notificationCalls.get())
        assertEquals(ReminderStatus.TRIGGERED, dao.getById(id)?.status)

        val reconcileResult = ReminderRedeliveryHandler.handle(
            entity = dao.getById(id)!!,
            coordinator = coordinator,
            now = now + 2
        )
        assertTrue(reconcileResult is RedeliveryHandleResult.Delivered)
        assertEquals(1, notificationCalls.get())
        assertEquals(ReminderStatus.DELIVERED, dao.getById(id)?.status)
    }

    @Test
    fun duplicateReceiverInvocationIsIdempotent() = runTest {
        val dao = HandlerFakeDao()
        val id = dao.insert(scheduledEntity())
        dao.update(dao.getById(id)!!.copy(status = ReminderStatus.TRIGGERED, triggeredAt = now))
        val notificationCalls = AtomicInteger(0)
        val coordinator = testCoordinator(dao, notificationCalls)

        val first = ReminderTriggerHandler.handle(dao.getById(id)!!, dao, coordinator, now)
        val second = ReminderTriggerHandler.handle(dao.getById(id)!!, dao, coordinator, now + 1)

        assertTrue(first is TriggerHandleResult.SkippedIdempotent)
        assertTrue(second is TriggerHandleResult.SkippedIdempotent)
        assertEquals(0, notificationCalls.get())
    }

    @Test
    fun triggerFromScheduledDeliversOnce() = runTest {
        val dao = HandlerFakeDao()
        val id = dao.insert(scheduledEntity())
        val notificationCalls = AtomicInteger(0)
        val coordinator = testCoordinator(dao, notificationCalls)

        val result = ReminderTriggerHandler.handle(dao.getById(id)!!, dao, coordinator, now)

        assertTrue(result is TriggerHandleResult.Delivered)
        assertEquals(ReminderStatus.DELIVERED, dao.getById(id)?.status)
        assertEquals(1, notificationCalls.get())
    }

    @Test
    fun notificationDeliveryFailureKeepsTriggered() = runTest {
        val dao = HandlerFakeDao()
        val id = dao.insert(scheduledEntity())
        val coordinator = ReminderDeliveryCoordinator(
            notificationDelivery = object : ReminderDeliveryService {
                override suspend fun deliver(reminder: Reminder) =
                    ReminderDeliveryResult.Failed("notification failed")
            },
            speechProvider = noOpSpeech(),
            voiceStateStore = HandlerFakeVoiceStore(dao),
            persistence = HandlerFakePersistence(dao),
            policy = alwaysProceedPolicy(),
            logger = NoOpReminderDeliveryLogger
        )

        val result = ReminderTriggerHandler.handle(dao.getById(id)!!, dao, coordinator, now)

        assertTrue(result is TriggerHandleResult.DeliveryFailed)
        assertEquals(ReminderStatus.TRIGGERED, dao.getById(id)?.status)
    }

    @Test
    fun snoozeResetsVoiceDeliveryCycle() = runTest {
        val dao = HandlerFakeDao()
        val id = dao.insert(
            scheduledEntity().copy(
                status = ReminderStatus.DELIVERED,
                deliveredAt = now,
                voiceDeliveryStatus = VoiceDeliveryStatus.SPOKEN,
                voiceDeliveredAt = now
            )
        )
        val voiceStore = HandlerFakeVoiceStore(dao)

        val result = ReminderSnoozeHandler.handle(
            reminderId = id,
            dao = dao,
            voiceStateStore = voiceStore,
            scheduleAlarm = { _, _, _ -> AlarmScheduleOutcome.Success },
            cancelAlarm = {},
            cancelNotification = {},
            now = now,
            snoozeMillis = TimeUnit.MINUTES.toMillis(10)
        )

        assertTrue(result is SnoozeHandleResult.Applied)
        assertEquals(VoiceDeliveryStatus.NOT_REQUESTED, dao.getById(id)?.voiceDeliveryStatus)
        assertEquals(ReminderStatus.SCHEDULED, dao.getById(id)?.status)
    }

    @Test
    fun duplicateCompleteIsNoOp() = runTest {
        val dao = HandlerFakeDao()
        val id = dao.insert(scheduledEntity().copy(status = ReminderStatus.COMPLETED, completedAt = now))
        var cancelAlarmCalls = 0

        val result = ReminderCompleteHandler.handle(
            id,
            dao,
            cancelAlarm = { cancelAlarmCalls++ },
            cancelNotification = {},
            now
        )

        assertTrue(result is CompleteHandleResult.SkippedIdempotent)
        assertEquals(0, cancelAlarmCalls)
    }

    @Test
    fun duplicateSnoozeWhileSchedulingIsNoOp() = runTest {
        val dao = HandlerFakeDao()
        val id = dao.insert(scheduledEntity().copy(status = ReminderStatus.SCHEDULING))
        var scheduleCalls = 0

        val result = ReminderSnoozeHandler.handle(
            id,
            dao,
            voiceStateStore = HandlerFakeVoiceStore(dao),
            scheduleAlarm = { _, _, _ ->
                scheduleCalls++
                AlarmScheduleOutcome.Success
            },
            cancelAlarm = {},
            cancelNotification = {},
            now = now,
            snoozeMillis = TimeUnit.MINUTES.toMillis(10)
        )

        assertTrue(result is SnoozeHandleResult.SkippedIdempotent)
        assertEquals(0, scheduleCalls)
    }

    @Test
    fun snoozeFromDeliveredReschedulesAtomically() = runTest {
        val dao = HandlerFakeDao()
        val id = dao.insert(
            scheduledEntity().copy(status = ReminderStatus.DELIVERED, deliveredAt = now)
        )
        var scheduleCalls = 0
        var cancelCalls = 0

        val result = ReminderSnoozeHandler.handle(
            id,
            dao,
            voiceStateStore = HandlerFakeVoiceStore(dao),
            scheduleAlarm = { _, trigger, _ ->
                scheduleCalls++
                assertTrue(trigger > now)
                AlarmScheduleOutcome.Success
            },
            cancelAlarm = { cancelCalls++ },
            cancelNotification = {},
            now = now,
            snoozeMillis = TimeUnit.MINUTES.toMillis(10)
        )

        assertTrue(result is SnoozeHandleResult.Applied)
        assertEquals(1, scheduleCalls)
        assertEquals(1, cancelCalls)
        assertEquals(ReminderStatus.SCHEDULED, dao.getById(id)?.status)
        assertEquals(1, dao.getById(id)?.snoozeCount)
    }

    @Test
    fun twoIndependentRemindersHaveDistinctPendingIntentCodes() {
        val id1 = 1L
        val id2 = 2L
        assertTrue(
            ReminderPendingIntentFactory.requestCode(1_000_000, id1) !=
                ReminderPendingIntentFactory.requestCode(1_000_000, id2)
        )
    }

    @Test
    fun notificationIdsAreStableAndUnique() {
        assertEquals(1, ReminderNotificationIds.forReminder(1L))
        assertEquals(2, ReminderNotificationIds.forReminder(2L))
        assertTrue(ReminderNotificationIds.forReminder(1L) != ReminderNotificationIds.forReminder(2L))
    }

    private fun testCoordinator(
        dao: HandlerFakeDao,
        notificationCalls: AtomicInteger
    ): ReminderDeliveryCoordinator = ReminderDeliveryCoordinator(
        notificationDelivery = object : ReminderDeliveryService {
            override suspend fun deliver(reminder: Reminder): ReminderDeliveryResult {
                notificationCalls.incrementAndGet()
                return ReminderDeliveryResult.Delivered
            }
        },
        speechProvider = noOpSpeech(),
        voiceStateStore = HandlerFakeVoiceStore(dao),
        persistence = HandlerFakePersistence(dao),
        policy = alwaysProceedPolicy(),
        logger = NoOpReminderDeliveryLogger
    )

    private fun scheduledEntity() = ReminderEntity(
        text = "Таблетка",
        triggerAt = now + 60_000,
        status = ReminderStatus.SCHEDULED,
        createdAt = now,
        updatedAt = now,
        precision = ReminderPrecision.EXACT,
        deliveryMode = ReminderDeliveryMode.NOTIFICATION_ONLY
    )
}

private fun noOpSpeech() = object : ReminderSpeechProvider {
    override suspend fun speak(reminderId: Long, text: String) = ReminderSpeechResult.Spoken
}

private fun alwaysProceedPolicy() = VoiceReminderPolicy(
    settings = object : com.care.voice.brain.reminder.VoiceReminderSettings {
        override val voiceRemindersEnabled = true
        override val quietHoursStartHour = 22
        override val quietHoursEndHour = 8
    },
    clock = object : com.care.voice.brain.reminder.Clock {
        override fun currentTimeMillis() = 1_700_000_000_000L
        override fun currentLocalHour() = 12
    },
    callState = object : com.care.voice.brain.reminder.CallStateProvider {
        override fun isPhoneCallActive() = false
    }
)

private class HandlerFakePersistence(
    private val dao: HandlerFakeDao
) : ReminderDeliveryPersistence {
    override suspend fun getSnapshot(reminderId: Long): ReminderDeliverySnapshot? =
        dao.getById(reminderId)?.toDeliverySnapshot()

    override suspend fun markNotificationDelivered(reminderId: Long, nowEpochMillis: Long): Boolean {
        val result = ReminderStateUpdater.applyTransition(
            dao = dao,
            id = reminderId,
            to = ReminderStatus.DELIVERED,
            now = nowEpochMillis
        ) { it.copy(deliveredAt = nowEpochMillis) }
        return result is StatusUpdateResult.Applied
    }
}

private class HandlerFakeVoiceStore(
    private val dao: HandlerFakeDao
) : ReminderVoiceStateStore {
    override suspend fun getStatus(reminderId: Long): VoiceDeliveryStatus =
        dao.getById(reminderId)?.voiceDeliveryStatus ?: VoiceDeliveryStatus.NOT_REQUESTED

    override suspend fun tryMarkPending(reminderId: Long, nowEpochMillis: Long): Boolean =
        dao.tryMarkVoicePending(reminderId, nowEpochMillis) == 1

    override suspend fun markSpoken(reminderId: Long, nowEpochMillis: Long) {
        dao.getById(reminderId)?.let {
            dao.update(it.copy(voiceDeliveryStatus = VoiceDeliveryStatus.SPOKEN, voiceDeliveredAt = nowEpochMillis))
        }
    }

    override suspend fun markSkipped(reminderId: Long, reason: VoiceSkipReason, nowEpochMillis: Long) {
        dao.getById(reminderId)?.let {
            dao.update(it.copy(voiceDeliveryStatus = VoiceDeliveryStatus.SKIPPED, voiceSkipReason = reason.name))
        }
    }

    override suspend fun markFailed(reminderId: Long, code: String, nowEpochMillis: Long) {
        dao.getById(reminderId)?.let {
            dao.update(it.copy(voiceDeliveryStatus = VoiceDeliveryStatus.FAILED, voiceFailureCode = code))
        }
    }

    override suspend fun resetForNewDeliveryCycle(reminderId: Long) {
        dao.getById(reminderId)?.let {
            dao.update(
                it.copy(
                    voiceDeliveryStatus = VoiceDeliveryStatus.NOT_REQUESTED,
                    voiceDeliveredAt = null,
                    voiceSkipReason = null,
                    voiceFailureCode = null,
                    voiceRequestedAt = null
                )
            )
        }
    }
}

private class HandlerFakeDao : ReminderDao {
    private var nextId = 1L
    private val store = mutableMapOf<Long, ReminderEntity>()

    override suspend fun insert(reminder: ReminderEntity): Long {
        val id = nextId++
        store[id] = reminder.copy(id = id)
        return id
    }

    override suspend fun update(reminder: ReminderEntity) {
        store[reminder.id] = reminder
    }

    override suspend fun getById(id: Long): ReminderEntity? = store[id]

    override suspend fun findByStatusAfterTrigger(status: ReminderStatus, now: Long): List<ReminderEntity> =
        store.values.filter { it.status == status && it.triggerAt > now }

    override suspend fun findByStatus(status: ReminderStatus): List<ReminderEntity> =
        store.values.filter { it.status == status }

    override suspend fun findByStatuses(statuses: List<ReminderStatus>): List<ReminderEntity> =
        store.values.filter { it.status in statuses }

    override suspend fun updateStatus(id: Long, status: ReminderStatus, updatedAt: Long) {
        store[id]?.let { store[id] = it.copy(status = status, updatedAt = updatedAt) }
    }

    override suspend fun tryMarkVoicePending(id: Long, now: Long): Int {
        val entity = store[id] ?: return 0
        if (entity.voiceDeliveryStatus != VoiceDeliveryStatus.NOT_REQUESTED) return 0
        store[id] = entity.copy(
            voiceDeliveryStatus = VoiceDeliveryStatus.PENDING,
            voiceRequestedAt = now,
            updatedAt = now
        )
        return 1
    }

    override suspend fun delete(reminder: ReminderEntity) {
        store.remove(reminder.id)
    }
}
