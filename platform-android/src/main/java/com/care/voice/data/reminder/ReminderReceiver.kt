package com.care.voice.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.care.voice.platform.android.reminder.ReminderPendingIntentFactory
import com.care.voice.platform.android.reminder.ReminderVoiceForegroundService

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderPendingIntentFactory.ACTION_REMINDER) return
        val reminderId = intent.getLongExtra(ReminderPendingIntentFactory.EXTRA_ID, -1L)
        if (reminderId <= 0L) return

        ReminderVoiceForegroundService.start(context.applicationContext, reminderId)
    }
}
