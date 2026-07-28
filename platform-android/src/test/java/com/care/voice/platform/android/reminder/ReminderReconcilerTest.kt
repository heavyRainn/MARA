package com.care.voice.platform.android.reminder

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
import com.care.voice.data.reminder.ReminderAlarmScheduler
import com.care.voice.data.repository.ReminderDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ReminderReconcilerTest {

    private val now = 1_700_000_000_000L

    @Test
    fun futureScheduledReminderIsRestoredAfterReboot() = runTest {
        val dao = ReconcilerFakeDao()
        val alarm = ReconcilerFakeAlarm()
        val id = dao.insert(
            entity(status = ReminderStatus.SCHEDULED, triggerAt = now + 120_000)
        )
        val reconciler = reconciler(dao, alarm, AtomicInteger(0))

        val report = reconciler.reconcile()

        assertEquals(1, report.restored)
        assertEquals(1, alarm.scheduleCalls)
        assertEquals(ReminderStatus.SCHEDULED, dao.getById(id)?.status)
    }

    @Test
    fun overdueScheduledMarkedMissed() = runTest {
        val dao = ReconcilerFakeDao()
        val id = dao.insert(
            entity(status = ReminderStatus.SCHEDULED, triggerAt = now - 60_000)
        )
        val reconciler = reconciler(dao, ReconcilerFakeAlarm(), AtomicInteger(0))

        val report = reconciler.reconcile()

        assertEquals(1, report.missed)
        assertEquals(ReminderStatus.FAILED, dao.getById(id)?.status)
        assertEquals("missed_after_deadline", dao.getById(id)?.failureMessage)
    }

    @Test
    fun staleSchedulingIsResolved() = runTest {
        val dao = ReconcilerFakeDao()
        val staleUpdatedAt = now - 10 * 60 * 1000
        val id = dao.insert(
            entity(
                status = ReminderStatus.SCHEDULING,
                triggerAt = now + 120_000,
                updatedAt = staleUpdatedAt
            )
        )
        val reconciler = ReminderReconciler(
            reminderDao = dao,
            alarmScheduler = ReconcilerFakeAlarm(),
            deliveryCoordinator = testCoordinator(dao, AtomicInteger(0)),
            nowMillis = { now },
            staleSchedulingThresholdMs = 5 * 60 * 1000
        )

        val report = reconciler.reconcile()

        assertEquals(1, report.staleResolved)
        assertEquals(ReminderStatus.SCHEDULED, dao.getById(id)?.status)
    }

    @Test
    fun triggeredWithoutDeliveredIsRedelivered() = runTest {
        val dao = ReconcilerFakeDao()
        val deliveryCalls = AtomicInteger(0)
        val id = dao.insert(
            entity(
                status = ReminderStatus.TRIGGERED,
                triggerAt = now - 1_000
            ).copy(triggeredAt = now - 1_000)
        )
        val reconciler = reconciler(dao, ReconcilerFakeAlarm(), deliveryCalls)

        val report = reconciler.reconcile()

        assertEquals(1, report.redelivered)
        assertEquals(1, deliveryCalls.get())
        assertEquals(ReminderStatus.DELIVERED, dao.getById(id)?.status)
    }

    private fun reconciler(
        dao: ReconcilerFakeDao,
        alarm: ReconcilerFakeAlarm,
        deliveryCalls: AtomicInteger
    ) = ReminderReconciler(
        reminderDao = dao,
        alarmScheduler = alarm,
        deliveryCoordinator = testCoordinator(dao, deliveryCalls),
        nowMillis = { now }
    )

    private fun testCoordinator(
        dao: ReconcilerFakeDao,
        deliveryCalls: AtomicInteger
    ): ReminderDeliveryCoordinator = ReminderDeliveryCoordinator(
        notificationDelivery = object : ReminderDeliveryService {
            override suspend fun deliver(reminder: Reminder): ReminderDeliveryResult {
                deliveryCalls.incrementAndGet()
                return ReminderDeliveryResult.Delivered
            }
        },
        speechProvider = object : ReminderSpeechProvider {
            override suspend fun speak(reminderId: Long, text: String) = ReminderSpeechResult.Spoken
        },
        voiceStateStore = ReconcilerFakeVoiceStore(dao),
        persistence = ReconcilerFakePersistence(dao),
        policy = VoiceReminderPolicy(
            settings = object : com.care.voice.brain.reminder.VoiceReminderSettings {
                override val voiceRemindersEnabled = true
                override val quietHoursStartHour = 22
                override val quietHoursEndHour = 8
            },
            clock = object : com.care.voice.brain.reminder.Clock {
                override fun currentTimeMillis() = now
                override fun currentLocalHour() = 12
            },
            callState = object : com.care.voice.brain.reminder.CallStateProvider {
                override fun isPhoneCallActive() = false
            }
        ),
        logger = NoOpReminderDeliveryLogger
    )

    private fun entity(
        status: ReminderStatus,
        triggerAt: Long,
        updatedAt: Long = now
    ) = ReminderEntity(
        text = "Таблетка",
        triggerAt = triggerAt,
        status = status,
        createdAt = now,
        updatedAt = updatedAt,
        precision = ReminderPrecision.EXACT,
        deliveryMode = ReminderDeliveryMode.NOTIFICATION_ONLY
    )
}

private class ReconcilerFakeAlarm : ReminderAlarmScheduler {
    var scheduleCalls = 0
    override fun schedule(reminderId: Long, triggerAt: Long, precision: ReminderPrecision): AlarmScheduleOutcome {
        scheduleCalls++
        return AlarmScheduleOutcome.Success
    }
    override fun cancel(reminderId: Long) = Unit
}

private class ReconcilerFakePersistence(
    private val dao: ReconcilerFakeDao
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

private class ReconcilerFakeVoiceStore(
    private val dao: ReconcilerFakeDao
) : ReminderVoiceStateStore {
    override suspend fun getStatus(reminderId: Long): VoiceDeliveryStatus =
        dao.getById(reminderId)?.voiceDeliveryStatus ?: VoiceDeliveryStatus.NOT_REQUESTED

    override suspend fun tryMarkPending(reminderId: Long, nowEpochMillis: Long): Boolean =
        dao.tryMarkVoicePending(reminderId, nowEpochMillis) == 1

    override suspend fun markSpoken(reminderId: Long, nowEpochMillis: Long) {
        dao.getById(reminderId)?.let {
            dao.update(it.copy(voiceDeliveryStatus = VoiceDeliveryStatus.SPOKEN))
        }
    }

    override suspend fun markSkipped(reminderId: Long, reason: VoiceSkipReason, nowEpochMillis: Long) {
        dao.getById(reminderId)?.let {
            dao.update(it.copy(voiceDeliveryStatus = VoiceDeliveryStatus.SKIPPED))
        }
    }

    override suspend fun markFailed(reminderId: Long, code: String, nowEpochMillis: Long) {
        dao.getById(reminderId)?.let {
            dao.update(it.copy(voiceDeliveryStatus = VoiceDeliveryStatus.FAILED))
        }
    }

    override suspend fun resetForNewDeliveryCycle(reminderId: Long) = Unit
}

private class ReconcilerFakeDao : ReminderDao {
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
        store[id] = entity.copy(voiceDeliveryStatus = VoiceDeliveryStatus.PENDING, voiceRequestedAt = now)
        return 1
    }

    override suspend fun delete(reminder: ReminderEntity) {
        store.remove(reminder.id)
    }
}
