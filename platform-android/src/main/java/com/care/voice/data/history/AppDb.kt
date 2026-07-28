package com.care.voice.data.history

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.care.voice.data.history.ReminderConverters
import com.care.voice.data.repository.*

@Database(
    entities = [
        MessageEntity::class,
        UserProfileEntity::class,
        ReminderEntity::class,
        ChatSummaryEntity::class,
        PendingActionEntity::class,
        ConversationSessionEntity::class,
        MemoryFactEntity::class,
        MemoryFactSourceEntity::class,
        MemoryTombstoneEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(ReminderConverters::class)
abstract class AppDb : RoomDatabase() {
    abstract fun messages(): MessagesDao
    abstract fun userProfile(): UserProfileDao
    abstract fun reminders(): ReminderDao
    abstract fun chatSummaryDao(): ChatSummaryDao
    abstract fun pendingActions(): PendingActionDao
    abstract fun conversationSessions(): ConversationSessionDao
    abstract fun memoryFacts(): MemoryFactDao
}
