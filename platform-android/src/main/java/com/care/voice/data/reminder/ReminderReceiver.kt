package com.care.voice.data.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.care.voice.platform.android.reminder.ReminderRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMINDER) return
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L)
        if (reminderId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (!ReminderRuntime.isInitialized()) {
                    Log.w(TAG, "ReminderRuntime is not initialized")
                    return@launch
                }

                val reminder = ReminderRuntime.reminderDao.getById(reminderId)
                if (reminder == null) {
                    Log.w(TAG, "Reminder $reminderId not found")
                    return@launch
                }

                showNotification(context, reminder.id, reminder.text)

                val interval = reminder.repeatIntervalMillis
                if (reminder.isRepeating && interval != null && interval > 0L) {
                    val next = reminder.copy(triggerAt = System.currentTimeMillis() + interval)
                    ReminderRuntime.reminderDao.update(next)
                    ReminderRuntime.reminderScheduler.schedule(next)
                } else {
                    ReminderRuntime.reminderDao.delete(reminder)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Reminder receiver failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, id: Long, text: String) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w(TAG, "POST_NOTIFICATIONS permission is not granted")
                return
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Напоминание")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        NotificationManagerCompat.from(context).notify(id.toNotificationId(), notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Напоминания", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Голосовые напоминания помощника"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    private fun Long.toNotificationId(): Int =
        if (this >= Int.MIN_VALUE.toLong() && this <= Int.MAX_VALUE.toLong()) this.toInt()
        else (this % Int.MAX_VALUE).toInt()

    companion object {
        private const val TAG = "ReminderReceiver"
        private const val CHANNEL_ID = "reminders"
    }
}
