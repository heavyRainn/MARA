package com.care.voice.platform.android.persistence

import android.content.Context
import androidx.room.Room
import com.care.voice.data.history.AppDb

object YasnaDatabase {
    fun create(context: Context): AppDb =
        Room.databaseBuilder(context, AppDb::class.java, "yasna.db")
            .addMigrations(MIGRATION_6_7)
            .build()
}
