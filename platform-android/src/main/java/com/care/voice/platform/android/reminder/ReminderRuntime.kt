package com.care.voice.platform.android.reminder

import com.care.voice.data.repository.ReminderDao
import com.care.voice.data.reminder.ReminderScheduler

/**
 * Runtime wiring for [ReminderReceiver] without depending on the app module.
 * Initialized from [com.care.voice.core.ServiceLocator] in the application layer.
 */
object ReminderRuntime {
    @Volatile
    private var initialized: Boolean = false

    lateinit var reminderDao: ReminderDao
        private set

    lateinit var reminderScheduler: ReminderScheduler
        private set

    fun initialize(dao: ReminderDao, scheduler: ReminderScheduler) {
        reminderDao = dao
        reminderScheduler = scheduler
        initialized = true
    }

    fun isInitialized(): Boolean = initialized
}
