package com.care.voice.ui.feature.speak

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.care.voice.BuildConfig
import com.care.voice.ui.components.AssistantBubble
import com.care.voice.ui.components.BigMicButton
import com.care.voice.ui.components.UserBubble
import com.care.voice.ui.speak.SpeakViewModel
import java.util.Locale

@Composable
fun SpeakScreen(vm: SpeakViewModel = viewModel()) {
    val permission = Manifest.permission.RECORD_AUDIO
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) vm.toggle(Locale("ru","RU"))
    }

    val ui by vm.state

    val micLabel = when {
        !hasPermission -> "Разрешите микрофон"
        ui.listening   -> "Слушаю… говорите"
        else           -> "Нажмите, чтобы говорить"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BigMicButton(
            active = ui.listening,
            label  = micLabel,
            onClick = {
                if (!hasPermission) requestPermission.launch(permission)
                else vm.toggle(Locale("ru","RU"))
            }
        )

        Spacer(Modifier.height(16.dp))

        if (ui.listening) {
            LinearProgressIndicator(
                modifier = Modifier.width(220.dp).height(6.dp),
                progress = { ((ui.rms + 2f) / 10f).coerceIn(0f, 1f) }
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.finalText.isNotBlank())
                UserBubble(text = ui.finalText.trim(), isFirst = ui.assistantText.isBlank())

            if (ui.assistantText.isNotBlank())
                AssistantBubble(
                    text = ui.assistantText,
                    isSpeaking = ui.speaking,
                    onRepeatVoice = { vm.repeatAssistant() },
                    onStopVoice = { vm.stopSpeaking() }
                )

            ui.error?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
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

        // -------- DEV-панель (оставляем как было) --------
        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(16.dp))
            var devText by remember { mutableStateOf("") }
            OutlinedTextField(
                value = devText,
                onValueChange = { devText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("DEV-ввод (обходит микрофон)") },
                placeholder = { Text("Например: расскажи анекдот") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (devText.isNotBlank()) vm.debugAskLLM(devText.trim())
                })
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { if (devText.isNotBlank()) vm.debugAskLLM(devText.trim()) }) {
                Text("Отправить в ИИ")
            }
        }
    }
}
