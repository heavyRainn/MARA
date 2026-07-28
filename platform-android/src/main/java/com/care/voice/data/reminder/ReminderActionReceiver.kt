package com.care.voice.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.care.voice.platform.android.reminder.CompleteHandleResult
import com.care.voice.platform.android.reminder.ReminderCompleteHandler
import com.care.voice.platform.android.reminder.ReminderDependencies
import com.care.voice.platform.android.reminder.ReminderLog
import com.care.voice.platform.android.reminder.ReminderPendingIntentFactory
import com.care.voice.platform.android.reminder.ReminderSnoozeHandler
import com.care.voice.platform.android.reminder.SnoozeHandleResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderPendingIntentFactory.EXTRA_ID, -1L)
        if (reminderId <= 0L) return

        val appContext = context.applicationContext
        val deps = ReminderDependencies.get(appContext)
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ReminderPendingIntentFactory.ACTION_COMPLETE -> {
                        ReminderCompleteHandler.handle(
                            reminderId = reminderId,
                            dao = deps.reminderDao,
                            cancelAlarm = { deps.alarmScheduler.cancel(it) },
                            cancelNotification = { deps.deliveryService.cancelNotification(it) },
                            now = System.currentTimeMillis()
                        )
                    }
                    ReminderPendingIntentFactory.ACTION_SNOOZE -> {
                        ReminderSnoozeHandler.handle(
                            reminderId = reminderId,
                            dao = deps.reminderDao,
                            voiceStateStore = deps.voiceStateStore,
                            scheduleAlarm = { id, trigger, precision ->
                                deps.alarmScheduler.schedule(id, trigger, precision)
                            },
                            cancelAlarm = { deps.alarmScheduler.cancel(it) },
                            cancelNotification = { deps.deliveryService.cancelNotification(it) },
                            now = System.currentTimeMillis(),
                            snoozeMillis = SNOOZE_MILLIS
                        )
                    }
                }
            } catch (t: Throwable) {
                ReminderLog.roomError(reminderId, intent.action ?: "action", t.message)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val SNOOZE_MILLIS = TimeUnit.MINUTES.toMillis(10)
    }
}
