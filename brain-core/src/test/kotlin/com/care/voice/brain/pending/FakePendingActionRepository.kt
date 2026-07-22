package com.care.voice.brain.pending

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class FakePendingActionRepository : PendingActionRepository {
    private val store = ConcurrentHashMap<String, PendingAction>()

    override suspend fun save(action: PendingAction) {
        store[action.id] = action
    }

    override suspend fun loadActive(): PendingAction? =
        store.values.firstOrNull { it.state == PendingActionState.WAITING_CONFIRMATION }

    override suspend fun getById(id: String): PendingAction? = store[id]

    override suspend fun updateState(id: String, state: PendingActionState) {
        store[id]?.let { store[id] = it.copy(state = state) }
    }

    override suspend fun expireOld(now: Instant) {
        store.entries.forEach { (id, action) ->
            if (action.state == PendingActionState.WAITING_CONFIRMATION && now.isAfter(action.expiresAt)) {
                store[id] = action.copy(state = PendingActionState.EXPIRED)
            }
        }
    }
}
