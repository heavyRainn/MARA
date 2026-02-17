package com.care.voice.data.reminder

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.care.voice.data.history.ReminderEntity
import java.util.concurrent.TimeUnit

class ReminderScheduler(
    private val context: Context
) {

    fun schedule(reminder: ReminderEntity) {
        val delay = reminder.triggerAt - System.currentTimeMillis()
        if (delay <= 0) return

        val work = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("text" to reminder.text))
            .build()

        WorkManager.getInstance(context).enqueue(work)
    }
}
