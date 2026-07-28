package com.care.voice.platform.android.reminder

import com.care.voice.brain.reminder.ReminderPrecision
import com.care.voice.brain.reminder.ReminderStatus
import com.care.voice.data.history.ReminderEntity
import com.care.voice.platform.android.reminder.markCompleted
import com.care.voice.platform.android.reminder.markDelivered
import com.care.voice.platform.android.reminder.markTriggered
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ReminderLifecycleTest {

    private val now = 1_700_000_000_000L

    @Test
    fun receiverStatusUpdatesTriggeredAndDelivered() = runTest {
        val dao = InMemoryReminderDao()
        val id = dao.insert(sampleEntity())
        assertTrue(dao.markTriggered(id, now) is StatusUpdateResult.Applied)
        assertEquals(ReminderStatus.TRIGGERED, dao.getById(id)?.status)
        assertNotNull(dao.getById(id)?.triggeredAt)
        assertTrue(dao.markDelivered(id, now + 1) is StatusUpdateResult.Applied)
        assertEquals(ReminderStatus.DELIVERED, dao.getById(id)?.status)
    }

    @Test
    fun invalidTransitionIsRejected() = runTest {
        val dao = InMemoryReminderDao()
        val id = dao.insert(sampleEntity().copy(status = ReminderStatus.COMPLETED, completedAt = now))
        val result = dao.markTriggered(id, now)
        assertTrue(result is StatusUpdateResult.Rejected)
        assertEquals(ReminderStatus.COMPLETED, dao.getById(id)?.status)
    }

    @Test
    fun completeMarksCompleted() = runTest {
        val dao = InMemoryReminderDao()
        val id = dao.insert(sampleEntity().copy(status = ReminderStatus.DELIVERED, deliveredAt = now))
        assertTrue(dao.markCompleted(id, now) is StatusUpdateResult.Applied)
        assertEquals(ReminderStatus.COMPLETED, dao.getById(id)?.status)
    }

    @Test
    fun snoozeUpdatesTriggerAt() = runTest {
        val dao = InMemoryReminderDao()
        val id = dao.insert(sampleEntity())
        val snoozeAt = now + TimeUnit.MINUTES.toMillis(10)
        dao.update(
            sampleEntity(id).copy(
                triggerAt = snoozeAt,
                status = ReminderStatus.SCHEDULED,
                snoozeCount = 1,
                lastSnoozedAt = now,
                updatedAt = now
            )
        )
        val updated = dao.getById(id)
        assertEquals(snoozeAt, updated?.triggerAt)
        assertEquals(1, updated?.snoozeCount)
    }

    private fun sampleEntity(id: Long = 0) = ReminderEntity(
        id = id,
        text = "Таблетка",
        triggerAt = now + 60_000,
        status = ReminderStatus.SCHEDULED,
        createdAt = now,
        updatedAt = now,
        precision = ReminderPrecision.EXACT
    )
}

private class InMemoryReminderDao : com.care.voice.data.repository.ReminderDao {
    private var nextId = 1L
    private val store = mutableMapOf<Long, ReminderEntity>()

    override suspend fun insert(reminder: ReminderEntity): Long {
        val id = if (reminder.id == 0L) nextId++ else reminder.id
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

    override suspend fun delete(reminder: ReminderEntity) {
        store.remove(reminder.id)
    }

    override suspend fun tryMarkVoicePending(id: Long, now: Long): Int = 0
}
