package com.care.voice.platform.android.persistence

import com.care.voice.brain.pending.PendingAction
import com.care.voice.brain.pending.PendingActionRepository
import com.care.voice.brain.pending.PendingActionState
import com.care.voice.data.repository.PendingActionDao
import java.time.Instant

class RoomPendingActionRepository(
    private val dao: PendingActionDao
) : PendingActionRepository {

    override suspend fun save(action: PendingAction) {
        dao.insert(action.toEntity())
    }

    override suspend fun loadActive(): PendingAction? =
        dao.loadActive()?.toBrain()

    override suspend fun getById(id: String): PendingAction? =
        dao.getById(id)?.toBrain()

    override suspend fun updateState(id: String, state: PendingActionState) {
        dao.updateState(id, state.name)
    }

    override suspend fun expireOld(now: Instant) {
        dao.expireOld(now.toEpochMilli())
    }
}
