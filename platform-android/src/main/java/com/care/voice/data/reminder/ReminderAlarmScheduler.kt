package com.care.voice.data.reminder

import com.care.voice.brain.reminder.ReminderPrecision

interface ReminderAlarmScheduler {
    fun schedule(reminderId: Long, triggerAt: Long, precision: ReminderPrecision): AlarmScheduleOutcome
    fun cancel(reminderId: Long)
}
