package com.care.voice.brain.pending

import java.time.Instant

data class PendingAction(
    val id: String,
    val type: PendingActionType,
    val payload: String,
    val state: PendingActionState,
    val createdAt: Instant,
    val expiresAt: Instant
)

enum class PendingActionType {
    CREATE_REMINDER,
    UPDATE_REMINDER,
    DELETE_REMINDER,
    CONFIRM_MEMORY,
    DELETE_MEMORY
}

enum class PendingActionState {
    WAITING_CONFIRMATION,
    COMPLETED,
    REJECTED,
    EXPIRED
}

interface PendingActionRepository {
    suspend fun save(action: PendingAction)
    suspend fun loadActive(): PendingAction?
    suspend fun getById(id: String): PendingAction?
    suspend fun updateState(id: String, state: PendingActionState)
    suspend fun expireOld(now: Instant)
}
