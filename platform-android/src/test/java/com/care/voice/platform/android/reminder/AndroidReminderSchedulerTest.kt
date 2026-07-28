package com.care.voice.platform.android.reminder

import com.care.voice.brain.reminder.ReminderDeliveryMode
import com.care.voice.brain.reminder.ReminderPrecision
import com.care.voice.brain.reminder.ReminderRequest
import com.care.voice.brain.reminder.ReminderStatus
import com.care.voice.brain.reminder.ScheduleReminderResult
import com.care.voice.data.history.ReminderEntity
import com.care.voice.data.reminder.AlarmScheduleOutcome
import com.care.voice.data.reminder.ReminderAlarmScheduler
import com.care.voice.data.repository.ReminderDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidReminderSchedulerTest {

    private val now = 1_700_000_000_000L

    @Test
    fun successOnlyAfterScheduledStatusAndAlarm() = runTest {
        val dao = FakeReminderDao()
        val alarm = FakeAlarmScheduler(AlarmScheduleOutcome.Success)
        val checker = FakeCapabilityChecker(notifications = true, exact = true)
        val scheduler = AndroidReminderScheduler(dao, alarm, checker) { now }

        val result = scheduler.schedule(
            ReminderRequest(
                text = "Таблетка",
                triggerAtMillis = now + 60_000,
                precision = ReminderPrecision.EXACT,
                isRepeating = false,
                repeatIntervalMillis = null
            )
        )

        assertTrue(result is ScheduleReminderResult.Success)
        assertEquals(ReminderStatus.SCHEDULED, dao.lastUpdated?.status)
        assertEquals(1, alarm.scheduleCalls)
    }

    @Test
    fun notificationDeniedReturnsRequired() = runTest {
        val dao = FakeReminderDao()
        val scheduler = AndroidReminderScheduler(
            dao,
            FakeAlarmScheduler(AlarmScheduleOutcome.Success),
            FakeCapabilityChecker(notifications = false, exact = true)
        ) { now }

        val result = scheduler.schedule(sampleRequest())
        assertEquals(ScheduleReminderResult.NotificationPermissionRequired, result)
        assertEquals(0, dao.insertCount)
    }

    @Test
    fun exactPermissionDeniedMarksFailed() = runTest {
        val dao = FakeReminderDao()
        val scheduler = AndroidReminderScheduler(
            dao,
            FakeAlarmScheduler(AlarmScheduleOutcome.ExactPermissionRequired),
            FakeCapabilityChecker(notifications = true, exact = false)
        ) { now }

        val result = scheduler.schedule(sampleRequest(ReminderPrecision.EXACT))
        assertEquals(ScheduleReminderResult.ExactAlarmPermissionRequired, result)
        assertEquals(ReminderStatus.FAILED, dao.lastUpdated?.status)
        assertEquals("exact_alarm_permission_required", dao.lastUpdated?.failureMessage)
    }

    @Test
    fun flexibleDoesNotRequireExactPermission() = runTest {
        val dao = FakeReminderDao()
        val scheduler = AndroidReminderScheduler(
            dao,
            FakeAlarmScheduler(AlarmScheduleOutcome.Success),
            FakeCapabilityChecker(notifications = true, exact = false)
        ) { now }

        val result = scheduler.schedule(sampleRequest(ReminderPrecision.FLEXIBLE))
        assertTrue(result is ScheduleReminderResult.Success)
    }

    @Test
    fun pastTimeReturnsInvalidTime() = runTest {
        val dao = FakeReminderDao()
        val scheduler = AndroidReminderScheduler(
            dao,
            FakeAlarmScheduler(AlarmScheduleOutcome.Success),
            FakeCapabilityChecker(true, true)
        ) { now }

        val result = scheduler.schedule(sampleRequest().copy(triggerAtMillis = now - 1))
        assertTrue(result is ScheduleReminderResult.InvalidTime)
    }

    @Test
    fun alarmFailureMarksFailedAndStoresReason() = runTest {
        val dao = FakeReminderDao()
        val scheduler = AndroidReminderScheduler(
            dao,
            FakeAlarmScheduler(AlarmScheduleOutcome.Failure("boom")),
            FakeCapabilityChecker(true, true)
        ) { now }

        val result = scheduler.schedule(sampleRequest())
        assertTrue(result is ScheduleReminderResult.Failure)
        assertEquals(ReminderStatus.FAILED, dao.lastUpdated?.status)
        assertEquals("boom", dao.lastUpdated?.failureReason)
    }

    @Test
    fun reschedulerRestoresFutureScheduled() = runTest {
        val dao = FakeReminderDao()
        val alarm = FakeAlarmScheduler(AlarmScheduleOutcome.Success)
        val rescheduler = ReminderRescheduler(dao, alarm) { now }
        dao.insert(
            ReminderEntity(
                id = 0,
                text = "x",
                triggerAt = now + 120_000,
                status = ReminderStatus.SCHEDULED,
                createdAt = now,
                updatedAt = now,
                precision = ReminderPrecision.EXACT
            )
        )
        val report = rescheduler.rescheduleAllFutureScheduled()
        assertEquals(1, report.rescheduled)
        assertEquals(0, report.skippedPast)
    }

    @Test
    fun reschedulerSkipsPastReminders() = runTest {
        val dao = FakeReminderDao()
        val alarm = FakeAlarmScheduler(AlarmScheduleOutcome.Success)
        val rescheduler = ReminderRescheduler(dao, alarm) { now }
        dao.insert(
            ReminderEntity(
                id = 0,
                text = "x",
                triggerAt = now - 120_000,
                status = ReminderStatus.SCHEDULED,
                createdAt = now,
                updatedAt = now,
                precision = ReminderPrecision.EXACT
            )
        )
        val report = rescheduler.rescheduleAllFutureScheduled()
        assertEquals(0, report.rescheduled)
        assertEquals(1, report.skippedPast)
        assertEquals(ReminderStatus.FAILED, dao.getById(1L)?.status)
    }

    private fun sampleRequest(precision: ReminderPrecision = ReminderPrecision.EXACT) =
        ReminderRequest(
            text = "Таблетка",
            triggerAtMillis = now + 60_000,
            precision = precision,
            isRepeating = false,
            repeatIntervalMillis = null,
            deliveryMode = ReminderDeliveryMode.NOTIFICATION_ONLY
        )
}

private class FakeCapabilityChecker(
    private val notifications: Boolean,
    private val exact: Boolean
) : ReminderCapabilityChecker {
    override fun areNotificationsAllowed(): Boolean = notifications
    override fun canScheduleExactAlarms(): Boolean = exact
}

private class FakeAlarmScheduler(
    private val outcome: AlarmScheduleOutcome
) : ReminderAlarmScheduler {
    var scheduleCalls = 0

    override fun schedule(reminderId: Long, triggerAt: Long, precision: ReminderPrecision): AlarmScheduleOutcome {
        scheduleCalls++
        return outcome
    }

    override fun cancel(reminderId: Long) = Unit
}

private class FakeReminderDao : ReminderDao {
    var insertCount = 0
    var lastUpdated: ReminderEntity? = null
    private var nextId = 1L
    private val store = mutableMapOf<Long, ReminderEntity>()

    override suspend fun insert(reminder: ReminderEntity): Long {
        insertCount++
        val id = nextId++
        store[id] = reminder.copy(id = id)
        lastUpdated = store[id]
        return id
    }

    override suspend fun update(reminder: ReminderEntity) {
        store[reminder.id] = reminder
        lastUpdated = reminder
    }

    override suspend fun getById(id: Long): ReminderEntity? = store[id]

    override suspend fun findByStatusAfterTrigger(status: ReminderStatus, now: Long): List<ReminderEntity> =
        store.values.filter { it.status == status && it.triggerAt > now }

    override suspend fun findByStatus(status: ReminderStatus): List<ReminderEntity> =
        store.values.filter { it.status == status }

    override suspend fun findByStatuses(statuses: List<ReminderStatus>): List<ReminderEntity> =
        store.values.filter { it.status in statuses }

    override suspend fun updateStatus(id: Long, status: ReminderStatus, updatedAt: Long) {
        store[id]?.let {
            val updated = it.copy(status = status, updatedAt = updatedAt)
            store[id] = updated
            lastUpdated = updated
        }
    }

    override suspend fun delete(reminder: ReminderEntity) {
        store.remove(reminder.id)
    }

    override suspend fun tryMarkVoicePending(id: Long, now: Long): Int = 0
}
