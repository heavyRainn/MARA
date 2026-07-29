package com.care.voice.platform.android.reminder

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.care.voice.platform.android.speech.YasnaSpeechHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while reminder voice is synthesized and played.
 * Without a foreground service, TTS from [ReminderReceiver] is often killed on Android 12+.
 */
class ReminderVoiceForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reminderId = intent?.getLongExtra(EXTRA_REMINDER_ID, -1L) ?: -1L
        if (reminderId <= 0L) {
            stopSelf()
            return START_NOT_STICKY
        }

        NotificationReminderDeliveryService.ensureChannel(this)
        val notification = buildForegroundNotification(reminderId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }

        scope.launch {
            try {
                YasnaSpeechHolder.get(this@ReminderVoiceForegroundService).warmUpPiperModel()
                val deps = ReminderDependencies.get(this@ReminderVoiceForegroundService)
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
                        now = System.currentTimeMillis(),
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
                        "rejected",
                    )
                }
            } catch (t: Throwable) {
                ReminderLog.roomError(reminderId, "trigger", t.message)
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun buildForegroundNotification(reminderId: Long): Notification =
        NotificationCompat.Builder(this, NotificationReminderDeliveryService.CHANNEL_ID)
            .setContentTitle(FOREGROUND_TITLE)
            .setContentText(FOREGROUND_TEXT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(ReminderPendingIntentFactory.openAppPendingIntent(this, reminderId))
            .build()

    companion object {
        private const val EXTRA_REMINDER_ID = "reminder_id"
        private const val FOREGROUND_NOTIFICATION_ID = 9100
        private const val FOREGROUND_TITLE = "Yasna"
        private const val FOREGROUND_TEXT = "Озвучиваю напоминание…"

        fun start(context: Context, reminderId: Long) {
            val intent = Intent(context, ReminderVoiceForegroundService::class.java)
                .putExtra(EXTRA_REMINDER_ID, reminderId)
            context.startForegroundService(intent)
        }
    }
}
