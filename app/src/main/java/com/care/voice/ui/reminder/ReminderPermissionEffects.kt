package com.care.voice.ui.reminder

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.care.voice.brain.ReminderSetupKind
import com.care.voice.ui.speak.SpeakViewModel

@Composable
fun ReminderPermissionEffects(vm: SpeakViewModel) {
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pendingId = vm.pendingNotificationRetryId
        if (granted && pendingId != null) {
            vm.clearPendingNotificationRetry()
            vm.retryPendingReminder(pendingId)
        } else {
            vm.clearPendingNotificationRetry()
        }
    }

    LaunchedEffect(vm) {
        vm.reminderSetupRequests.collect { request ->
            when (request.kind) {
                ReminderSetupKind.NOTIFICATION_PERMISSION -> {
                    vm.setPendingNotificationRetry(request.pendingActionId)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                ReminderSetupKind.EXACT_ALARM_PERMISSION -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                }
            }
        }
    }
}
