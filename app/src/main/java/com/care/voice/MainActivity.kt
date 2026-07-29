package com.care.voice

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import com.care.voice.core.ServiceLocator
import com.care.voice.ui.YasnaMainScreen
import com.care.voice.ui.reminder.ReminderPermissionEffects
import com.care.voice.ui.speak.SpeakViewModel
import com.care.voice.ui.theme.YasnaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        appScope.launch {
            ServiceLocator.wirePlatformRuntime()
            runCatching { ServiceLocator.reconcileReminders() }
        }
    }
}

class MainActivity : ComponentActivity() {
    private val speakViewModel: SpeakViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YasnaTheme {
                ReminderPermissionEffects(speakViewModel)
                Surface { YasnaMainScreen(speakViewModel) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        speakViewModel.onActivityResumed()
    }
}
