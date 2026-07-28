package com.care.voice.platform.android.persistence

import android.content.Context
import androidx.room.Room
import com.care.voice.data.history.AppDb

object YasnaDatabase {
    @Volatile
    private var instance: AppDb? = null

    fun get(context: Context): AppDb {
        val appContext = context.applicationContext
        return instance ?: synchronized(this) {
            instance ?: build(appContext).also { instance = it }
        }
    }

    fun create(context: Context): AppDb = get(context)

    private fun build(context: Context): AppDb =
        Room.databaseBuilder(context, AppDb::class.java, "yasna.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
}
