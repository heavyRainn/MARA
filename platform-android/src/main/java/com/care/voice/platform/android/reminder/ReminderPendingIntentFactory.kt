package com.care.voice.platform.android.reminder

import android.net.Uri
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.care.voice.data.reminder.ReminderActionReceiver
import com.care.voice.data.reminder.ReminderReceiver

object ReminderPendingIntentFactory {
    const val ACTION_REMINDER = "com.care.voice.action.REMINDER"
    const val ACTION_COMPLETE = "com.care.voice.action.REMINDER_COMPLETE"
    const val ACTION_SNOOZE = "com.care.voice.action.REMINDER_SNOOZE"
    const val ACTION_OPEN = "com.care.voice.action.REMINDER_OPEN"

    const val EXTRA_ID = "reminder_id"

    private const val SCHEME = "yasna"
    private const val AUTHORITY = "reminder"

    private const val REQUEST_ALARM = 1_000_000
    private const val REQUEST_OPEN = 2_000_000
    private const val REQUEST_COMPLETE = 3_000_000
    private const val REQUEST_SNOOZE = 4_000_000

    fun alarmPendingIntent(context: Context, reminderId: Long, flags: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            data = reminderUri(reminderId, "alarm")
            putExtra(EXTRA_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(REQUEST_ALARM, reminderId),
            intent,
            immutableFlags(flags)
        )
    }

    fun openAppPendingIntent(context: Context, reminderId: Long): PendingIntent {
        val launchIntent = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName)) {
            "Launch intent not found"
        }.apply {
            action = ACTION_OPEN
            data = reminderUri(reminderId, "open")
            putExtra(EXTRA_ID, reminderId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode(REQUEST_OPEN, reminderId),
            launchIntent,
            immutableFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }

    fun completePendingIntent(context: Context, reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ACTION_COMPLETE
            data = reminderUri(reminderId, "complete")
            putExtra(EXTRA_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(REQUEST_COMPLETE, reminderId),
            intent,
            immutableFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }

    fun snoozePendingIntent(context: Context, reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ACTION_SNOOZE
            data = reminderUri(reminderId, "snooze")
            putExtra(EXTRA_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(REQUEST_SNOOZE, reminderId),
            intent,
            immutableFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }

    fun cancelAlarm(context: Context, reminderId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            data = reminderUri(reminderId, "alarm")
            putExtra(EXTRA_ID, reminderId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(REQUEST_ALARM, reminderId),
            intent,
            immutableFlags(PendingIntent.FLAG_NO_CREATE)
        ) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarmManager.cancel(pi)
    }

    fun reminderUri(reminderId: Long, segment: String): Uri =
        Uri.parse(reminderUriString(reminderId, segment))

    fun reminderUriString(reminderId: Long, segment: String): String =
        "$SCHEME://$AUTHORITY/$reminderId/$segment"

    fun requestCode(base: Int, reminderId: Long): Int {
        var hash = base
        hash = 31 * hash + reminderId.hashCode()
        return hash and 0x7FFFFFFF
    }

    private fun immutableFlags(extra: Int): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or extra
        } else {
            extra
        }
}
