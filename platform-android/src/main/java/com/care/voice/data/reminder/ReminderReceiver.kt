package com.care.voice.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.care.voice.platform.android.reminder.NotificationReminderDeliveryService
import com.care.voice.platform.android.reminder.ReminderDependencies
import com.care.voice.platform.android.reminder.ReminderLog
import com.care.voice.platform.android.reminder.ReminderPendingIntentFactory
import com.care.voice.platform.android.reminder.ReminderTriggerHandler
import com.care.voice.platform.android.reminder.TriggerHandleResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderPendingIntentFactory.ACTION_REMINDER) return
        val reminderId = intent.getLongExtra(ReminderPendingIntentFactory.EXTRA_ID, -1L)
        if (reminderId <= 0L) return

        val appContext = context.applicationContext
        val deps = ReminderDependencies.get(appContext)
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                NotificationReminderDeliveryService.ensureChannel(appContext)
                val entity = deps.reminderDao.getById(reminderId)
                if (entity == null) {
                    ReminderLog.receiver(reminderId, "trigger", null, null, "not_found")
                    return@launch
                }

                when (
                    ReminderTriggerHandler.handle(
                        entity = entity,
                        dao = deps.reminderDao,
                        coordinator = deps.deliveryCoordinator,
                        now = System.currentTimeMillis()
                    )
                ) {
                    TriggerHandleResult.SkippedIdempotent -> Unit
                    TriggerHandleResult.NotFound -> Unit
                    TriggerHandleResult.Delivered -> Unit
                    is TriggerHandleResult.DeliveryFailed -> Unit
                    is TriggerHandleResult.Rejected -> ReminderLog.receiver(
                        reminderId,
                        "trigger",
                        entity.status.name,
                        null,
                        "rejected"
                    )
                }
            } catch (t: Throwable) {
                ReminderLog.roomError(reminderId, "trigger", t.message)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
