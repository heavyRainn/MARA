package com.care.voice.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.care.voice.platform.android.reminder.ReminderDependencies
import com.care.voice.platform.android.reminder.ReminderLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val report = ReminderDependencies.get(context.applicationContext).reconciler.reconcile()
                ReminderLog.reconciler(
                    "boot restored=${report.restored} missed=${report.missed} " +
                        "stale=${report.staleResolved} redelivered=${report.redelivered}"
                )
            } catch (t: Throwable) {
                ReminderLog.reconciler("boot_failed:${t.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
