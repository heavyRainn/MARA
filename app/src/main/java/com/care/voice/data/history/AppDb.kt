package com.care.voice.data.history

import androidx.room.Database
import androidx.room.RoomDatabase
import com.care.voice.data.repository.ConversationSummaryDao
import com.care.voice.data.repository.ReminderDao
import com.care.voice.data.repository.UserProfileDao

@Database(
    entities = [
        MessageEntity::class,
        ConversationSummary::class,
        UserProfileEntity::class,
        ReminderEntity::class
    ],
    version = 3,              // ← ОБЯЗАТЕЛЬНО увеличить
    exportSchema = true
)
abstract class AppDb : RoomDatabase() {

    abstract fun messages(): MessagesDao
    abstract fun summary(): ConversationSummaryDao
    abstract fun userProfile(): UserProfileDao
    abstract fun reminders(): ReminderDao

}
