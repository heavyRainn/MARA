package com.care.voice.platform.android.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.care.voice.brain.reminder.Reminder
import com.care.voice.brain.reminder.ReminderDeliveryResult
import com.care.voice.brain.reminder.ReminderDeliveryService

class NotificationReminderDeliveryService(
    private val context: Context,
    private val capabilityChecker: ReminderCapabilityChecker
) : ReminderDeliveryService {

    override suspend fun deliver(reminder: Reminder): ReminderDeliveryResult {
        if (!capabilityChecker.areNotificationsAllowed()) {
            return ReminderDeliveryResult.Failed("notifications not allowed")
        }

        ensureChannel(context)

        val textHash = reminder.text.hashCode()
        ReminderLog.debugText(reminder.id, reminder.text.length, textHash)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(TITLE)
            .setContentText(reminder.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(ReminderPendingIntentFactory.openAppPendingIntent(context, reminder.id))
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                ACTION_COMPLETE_LABEL,
                ReminderPendingIntentFactory.completePendingIntent(context, reminder.id)
            )
            .addAction(
                android.R.drawable.ic_menu_recent_history,
                ACTION_SNOOZE_LABEL,
                ReminderPendingIntentFactory.snoozePendingIntent(context, reminder.id)
            )
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(
                ReminderNotificationIds.forReminder(reminder.id),
                notification
            )
            ReminderLog.delivery(reminder.id, success = true)
            ReminderDeliveryResult.Delivered
        } catch (t: Throwable) {
            ReminderLog.delivery(reminder.id, success = false, failureReason = t.message)
            ReminderDeliveryResult.Failed(t.message ?: "notification failed")
        }
    }

    fun cancelNotification(reminderId: Long) {
        NotificationManagerCompat.from(context).cancel(ReminderNotificationIds.forReminder(reminderId))
    }

    companion object {
        const val CHANNEL_ID = "yasna_reminders"
        private const val CHANNEL_NAME = "Напоминания Yasna"
        private const val TITLE = "Yasna"
        private const val ACTION_COMPLETE_LABEL = "Выполнено"
        private const val ACTION_SNOOZE_LABEL = "Через 10 минут"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Напоминания голосового помощника Yasna"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
