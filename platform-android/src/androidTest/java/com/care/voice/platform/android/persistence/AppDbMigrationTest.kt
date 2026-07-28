package com.care.voice.platform.android.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.care.voice.data.history.AppDb
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AppDbMigrationTest {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDb::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2To3To4() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS reminders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    text TEXT NOT NULL,
                    triggerAt INTEGER NOT NULL,
                    isRepeating INTEGER NOT NULL DEFAULT 0,
                    repeatIntervalMillis INTEGER
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)
        helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)
        helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)
    }
}
