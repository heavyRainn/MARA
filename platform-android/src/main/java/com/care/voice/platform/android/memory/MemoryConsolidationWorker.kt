package com.care.voice.platform.android.memory

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.care.voice.brain.memory.consolidate.DefaultMemoryConsolidator
import com.care.voice.data.history.ChatHistoryRepository
import com.care.voice.platform.android.persistence.RoomMemoryRepository
import com.care.voice.platform.android.persistence.RoomMemoryStore
import com.care.voice.platform.android.persistence.YasnaDatabase
import java.time.Instant

class MemoryConsolidationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val db = YasnaDatabase.create(applicationContext)
            val historyRepo = ChatHistoryRepository(db.messages())
            val memoryStore = RoomMemoryStore(historyRepo, db.userProfile(), db.chatSummaryDao(), historyTail = 8)
            val memoryRepository = RoomMemoryRepository(db.memoryFacts())
            val consolidator = DefaultMemoryConsolidator(memoryRepository, memoryStore)
            consolidator.consolidate(Instant.now())
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
