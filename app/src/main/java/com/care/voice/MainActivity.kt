package com.care.voice

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.care.voice.core.ServiceLocator
import com.care.voice.ui.feature.speak.SpeakScreen
import com.care.voice.ui.theme.YasnaTheme

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.app = this
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
