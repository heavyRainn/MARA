package com.care.voice.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.care.voice.data.history.ReminderEntity

class ReminderScheduler(private val context: Context) {

    fun schedule(reminder: ReminderEntity): Boolean {
        if (reminder.triggerAt <= System.currentTimeMillis()) {
            Log.w(TAG, "Skip reminder ${reminder.id}: triggerAt is in the past")
            return false
        }
        return scheduleRaw(reminder.id, reminder.triggerAt)
    }

    fun cancel(reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        pendingIntentOrNull(reminderId, PendingIntent.FLAG_NO_CREATE)?.let { alarmManager.cancel(it) }
    }

    internal fun scheduleRaw(id: Long, triggerAt: Long): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(id, PendingIntent.FLAG_UPDATE_CURRENT)

        return try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms() -> {
                    Log.w(TAG, "Exact alarms are not allowed. Falling back to inexact alarm.")
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
                else -> alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            Log.d(TAG, "Scheduled reminder $id at $triggerAt")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to schedule reminder $id", t)
            false
        }
    }

    private fun pendingIntent(reminderId: Long, extraFlags: Int): PendingIntent =
        requireNotNull(pendingIntentOrNull(reminderId, extraFlags))

    private fun pendingIntentOrNull(reminderId: Long, extraFlags: Int): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(EXTRA_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toIntSafely(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or extraFlags
        )
    }

    private fun Long.toIntSafely(): Int =
        if (this >= Int.MIN_VALUE.toLong() && this <= Int.MAX_VALUE.toLong()) this.toInt()
        else (this % Int.MAX_VALUE).toInt()

    companion object {
        private const val TAG = "ReminderScheduler"
        const val ACTION_REMINDER = "com.care.voice.action.REMINDER"
        const val EXTRA_ID = "reminder_id"
    }
}
