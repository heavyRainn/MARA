package com.care.voice.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val text = inputData.getString("text") ?: return Result.failure()

        showNotification(text)
        speakAndWait(text)

        return Result.success()
    }

    private fun showNotification(text: String) {
        val channelId = "reminders"

        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Напоминания",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Напоминание")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)

        Log.d("REMINDER", "Notification shown")
    }

    private suspend fun speakAndWait(text: String) =
        suspendCancellableCoroutine<Unit> { continuation ->

            lateinit var tts: TextToSpeech

            tts = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {

                    tts.language = Locale("ru", "RU")

                    tts.setOnUtteranceProgressListener(
                        object : android.speech.tts.UtteranceProgressListener() {

                            override fun onStart(utteranceId: String?) {
                                Log.d("REMINDER", "TTS started")
                            }

                            override fun onDone(utteranceId: String?) {
                                Log.d("REMINDER", "TTS finished")
                                tts.shutdown()
                                continuation.resume(Unit)
                            }

                            override fun onError(utteranceId: String?) {
                                Log.d("REMINDER", "TTS error")
                                tts.shutdown()
                                continuation.resume(Unit)
                            }
                        }
                    )

                    tts.speak(
                        text,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "reminder"
                    )
                } else {
                    continuation.resume(Unit)
                }
            }
        }
}
