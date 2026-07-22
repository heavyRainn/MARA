package com.care.voice

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.care.voice.core.ServiceLocator
import com.care.voice.ui.feature.speak.SpeakScreen
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
        // Room/Retrofit init off main thread — was blocking UI for ~950ms at startup.
        appScope.launch {
            ServiceLocator.wirePlatformRuntime()
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YasnaTheme {
                Surface { SpeakScreen() }
            }
        }
    }
}
