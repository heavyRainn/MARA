package com.care.voice.platform.android.persistence

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminders ADD COLUMN status TEXT NOT NULL DEFAULT 'SCHEDULED'")
        db.execSQL("ALTER TABLE reminders ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE reminders ADD COLUMN scheduledAt INTEGER")
        db.execSQL("ALTER TABLE reminders ADD COLUMN triggeredAt INTEGER")
        db.execSQL("ALTER TABLE reminders ADD COLUMN deliveredAt INTEGER")
        db.execSQL("ALTER TABLE reminders ADD COLUMN completedAt INTEGER")
        db.execSQL("ALTER TABLE reminders ADD COLUMN failureReason TEXT")
        db.execSQL("ALTER TABLE reminders ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE reminders ADD COLUMN precision TEXT NOT NULL DEFAULT 'EXACT'")
        db.execSQL("ALTER TABLE reminders ADD COLUMN deliveryMode TEXT NOT NULL DEFAULT 'NOTIFICATION_ONLY'")
        db.execSQL("ALTER TABLE reminders ADD COLUMN snoozeCount INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE reminders ADD COLUMN lastSnoozedAt INTEGER")
        db.execSQL(
            """
            UPDATE reminders SET
                createdAt = CASE WHEN createdAt = 0 THEN triggerAt ELSE createdAt END,
                updatedAt = CASE WHEN updatedAt = 0 THEN triggerAt ELSE updatedAt END,
                status = 'SCHEDULED'
            WHERE status = 'SCHEDULED' OR status IS NOT NULL
            """.trimIndent()
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminders ADD COLUMN failureCode TEXT")
        db.execSQL("ALTER TABLE reminders ADD COLUMN failureMessage TEXT")
        db.execSQL("ALTER TABLE reminders ADD COLUMN failedAt INTEGER")
        db.execSQL(
            """
            UPDATE reminders SET
                failureMessage = failureReason,
                failedAt = CASE WHEN failureReason IS NOT NULL THEN updatedAt ELSE NULL END
            WHERE failureReason IS NOT NULL
            """.trimIndent()
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE reminders ADD COLUMN voiceDeliveryStatus TEXT NOT NULL DEFAULT 'NOT_REQUESTED'"
        )
        db.execSQL("ALTER TABLE reminders ADD COLUMN voiceDeliveredAt INTEGER")
        db.execSQL("ALTER TABLE reminders ADD COLUMN voiceSkipReason TEXT")
        db.execSQL("ALTER TABLE reminders ADD COLUMN voiceFailureCode TEXT")
        db.execSQL("ALTER TABLE reminders ADD COLUMN voiceRequestedAt INTEGER")
    }
}
