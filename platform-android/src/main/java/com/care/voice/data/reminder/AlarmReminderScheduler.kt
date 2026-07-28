package com.care.voice.data.reminder

import android.app.AlarmManager
import android.content.Context
import com.care.voice.brain.reminder.ReminderPrecision
import com.care.voice.platform.android.reminder.ReminderPendingIntentFactory

sealed class AlarmScheduleOutcome {
    data object Success : AlarmScheduleOutcome()
    data object ExactPermissionRequired : AlarmScheduleOutcome()
    data class Failure(val reason: String) : AlarmScheduleOutcome()
}

class AlarmReminderScheduler(
    private val context: Context,
    private val canScheduleExactAlarms: () -> Boolean = {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.canScheduleExactAlarms()
        } else {
            true
        }
    }
) : ReminderAlarmScheduler {

    override fun schedule(reminderId: Long, triggerAt: Long, precision: ReminderPrecision): AlarmScheduleOutcome {
        if (triggerAt <= System.currentTimeMillis()) {
            return AlarmScheduleOutcome.Failure("triggerAt in past")
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = ReminderPendingIntentFactory.alarmPendingIntent(
            context,
            reminderId,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        return try {
            when (precision) {
                ReminderPrecision.EXACT -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                        !canScheduleExactAlarms()
                    ) {
                        return AlarmScheduleOutcome.ExactPermissionRequired
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    }
                    AlarmScheduleOutcome.Success
                }
                ReminderPrecision.FLEXIBLE -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    }
                    AlarmScheduleOutcome.Success
                }
            }
        } catch (t: Throwable) {
            AlarmScheduleOutcome.Failure(t.message ?: "alarm scheduling failed")
        }
    }

    override fun cancel(reminderId: Long) {
        ReminderPendingIntentFactory.cancelAlarm(context, reminderId)
    }
}
