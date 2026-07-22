package com.care.voice.platform.android.persistence

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN message_uid TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE messages ADD COLUMN state TEXT NOT NULL DEFAULT 'ACTIVE'")
        db.execSQL("UPDATE messages SET message_uid = 'legacy-' || id WHERE message_uid = ''")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS pending_actions (
                id TEXT NOT NULL PRIMARY KEY,
                type TEXT NOT NULL,
                payload TEXT NOT NULL,
                state TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_actions_state ON pending_actions(state)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_actions_expires_at ON pending_actions(expires_at)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS conversation_sessions (
                id TEXT NOT NULL PRIMARY KEY,
                started_at INTEGER NOT NULL,
                last_activity_at INTEGER NOT NULL,
                exclude_from_extraction INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS memory_facts (
                id TEXT NOT NULL PRIMARY KEY,
                subject_type TEXT NOT NULL,
                subject_relation TEXT,
                subject_name TEXT,
                type TEXT NOT NULL,
                fact_key TEXT NOT NULL,
                value TEXT NOT NULL,
                confidence REAL NOT NULL,
                importance INTEGER NOT NULL,
                valid_from INTEGER,
                valid_until INTEGER,
                status TEXT NOT NULL,
                confirmation_status TEXT NOT NULL,
                sensitivity TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                last_confirmed_at INTEGER,
                last_used_at INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_facts_type ON memory_facts(type)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_facts_fact_key ON memory_facts(fact_key)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_facts_status ON memory_facts(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_facts_valid_until ON memory_facts(valid_until)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_facts_subject_type ON memory_facts(subject_type)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_facts_updated_at ON memory_facts(updated_at)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS memory_fact_sources (
                id TEXT NOT NULL PRIMARY KEY,
                memory_fact_id TEXT NOT NULL,
                message_id TEXT NOT NULL,
                source_type TEXT NOT NULL,
                excerpt TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_fact_sources_memory_fact_id ON memory_fact_sources(memory_fact_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_fact_sources_message_id ON memory_fact_sources(message_id)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS memory_tombstones (
                id TEXT NOT NULL PRIMARY KEY,
                subject_type TEXT NOT NULL,
                subject_relation TEXT,
                subject_name TEXT,
                type TEXT NOT NULL,
                tombstone_key TEXT NOT NULL,
                value_hash TEXT,
                created_at INTEGER NOT NULL,
                reason TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_tombstones_subject_type ON memory_tombstones(subject_type)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_tombstones_type ON memory_tombstones(type)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_tombstones_tombstone_key ON memory_tombstones(tombstone_key)")

        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT OR IGNORE INTO conversation_sessions (id, started_at, last_activity_at, exclude_from_extraction) VALUES ('default', $now, $now, 0)"
        )
    }
}
