package com.care.voice.ui.feature.speak

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.care.voice.ui.components.AssistantBubble
import com.care.voice.ui.components.Backdrop
import com.care.voice.ui.components.BigMicButton
import com.care.voice.platform.voice.YasnaSpeechLog
import com.care.voice.ui.components.UserBubble
import com.care.voice.ui.speak.SpeakViewModel
import java.util.Locale

@Composable
fun SpeakScreen(vm: SpeakViewModel = viewModel()) {
    val permission = Manifest.permission.RECORD_AUDIO
    val hasPermissionState = remember { mutableStateOf(false) }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermissionState.value = granted
        YasnaSpeechLog.d("RECORD_AUDIO permission state=$granted (after request)")
        if (granted) vm.toggle(Locale.forLanguageTag("ru-RU"))
    }

    val context = LocalContext.current
    LaunchedEffect(context, permission) {
        hasPermissionState.value =
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    val ui by vm.state
    val hasPermission = hasPermissionState.value

    val micLabel = when {
        !hasPermission -> "Разрешите микрофон"
        ui.listening -> "Слушаю… говорите"
        else -> "Нажмите, чтобы говорить"
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BigMicButton(
                active = ui.listening,
                label = micLabel,
                onClick = {
                    YasnaSpeechLog.d("microphone button clicked hasPermission=$hasPermission")
                    if (!hasPermission) {
                        YasnaSpeechLog.d("launching RECORD_AUDIO permission request")
                        requestPermission.launch(permission)
                    } else {
                        vm.toggle(Locale.forLanguageTag("ru-RU"))
                    }
                }
            )

            Spacer(Modifier.height(12.dp))

            if (ui.listening) {
                LinearProgressIndicator(
                    modifier = Modifier.width(220.dp).height(6.dp),
                    progress = { ((ui.rms + 2f) / 10f).coerceIn(0f, 1f) }
                )
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (ui.finalText.isNotBlank()) {
                    UserBubble(text = ui.finalText.trim(), isFirst = ui.assistantText.isBlank())
                }

                if (ui.assistantText.isNotBlank()) {
                    AssistantBubble(
                        text = ui.assistantText,
                        isSpeaking = ui.speaking,
                        onRepeatVoice = { vm.repeatAssistant() },
                        onStopVoice = { vm.stopSpeaking() }
                    )
                }

                ui.error?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
