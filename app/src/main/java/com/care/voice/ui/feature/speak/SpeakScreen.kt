package com.care.voice.ui.speak

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.care.voice.BuildConfig
import com.care.voice.ui.components.BigMicButton
import java.util.Locale

@Composable
fun SpeakScreen(vm: SpeakViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    val permission = Manifest.permission.RECORD_AUDIO
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) vm.toggle(Locale("ru","RU"))
    }

    val ui by vm.state

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BigMicButton(active = ui.listening, onClick = {
            if (!hasPermission) requestPermission.launch(permission) else vm.toggle(Locale("ru","RU"))
        })

        Spacer(Modifier.height(12.dp))

        if (ui.listening) {
            LinearProgressIndicator(
                modifier = Modifier.width(220.dp).height(6.dp),
                progress = { ((ui.rms + 2f) / 10f).coerceIn(0f, 1f) }
            )
            Spacer(Modifier.height(8.dp))
        }

        Text(
            text = when {
                !hasPermission -> "Нужно разрешение на микрофон"
                ui.listening -> "Слушаю… говорите"
                else -> "Нажмите, чтобы говорить"
            },
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(20.dp))

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.finalText.isNotBlank()) {
                UserBubble(text = ui.finalText.trim())
            }

            if (ui.partial.isNotBlank()) {
                Text(
                    ui.partial,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
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

        Spacer(Modifier.height(16.dp))

        if (!hasPermission) {
            AssistChip(onClick = { requestPermission.launch(permission) }, label = { Text("Разрешить микрофон") })
        }

        // DEV-панель КОММЕНТИРОВАТЬ ПРИ АПК
//        if (BuildConfig.DEBUG) {
//            Spacer(Modifier.height(16.dp))
//            var devText by remember { mutableStateOf("расскажи анекдот") }
//            OutlinedTextField(
//                value = devText,
//                onValueChange = { devText = it },
//                label = { Text("DEV-ввод (обходит микрофон)") },
//                modifier = Modifier.fillMaxWidth()
//            )
//            Spacer(Modifier.height(8.dp))
//            Button(onClick = { vm.debugAskLLM(devText) }) { Text("Отправить в ИИ") }
//        }
    }
}

/* ===================== Вспомогательные компоненты ===================== */

@Composable
private fun UserBubble(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
    ) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun AssistantBubble(
    text: String,
    isSpeaking: Boolean,
    onRepeatVoice: (() -> Unit)? = null,
    onStopVoice: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf(16.sp) }
    val clipboard = LocalClipboardManager.current

    Surface(
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Ассистент",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.weight(1f))

                // Кнопка Стоп — видна только во время озвучки
                if (isSpeaking && onStopVoice != null) {
                    TextButton(onClick = onStopVoice) { Text("Стоп") }
                    Spacer(Modifier.width(4.dp))
                }

                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Меню")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Скопировать") },
                        onClick = {
                            clipboard.setText(AnnotatedString(text))
                            menuExpanded = false
                        }
                    )
                    // Крупнее
                    DropdownMenuItem(
                        text = { Text("Крупнее") },
                        onClick = {
                            fontSize = (fontSize.value + 2f).sp
                            menuExpanded = false
                        }
                    )

                    // Мельче
                    DropdownMenuItem(
                        text = { Text("Мельче") },
                        onClick = {
                            fontSize = maxOf(12f, fontSize.value - 2f).sp
                            menuExpanded = false
                        }
                    )

                    if (onRepeatVoice != null) {
                        DropdownMenuItem(text = { Text("Повторить голосом") }, onClick = {
                            onRepeatVoice(); menuExpanded = false
                        })
                    }
                    if (isSpeaking && onStopVoice != null) {
                        DropdownMenuItem(text = { Text("Остановить речь") }, onClick = {
                            onStopVoice(); menuExpanded = false
                        })
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = text,
                fontSize = fontSize,
                lineHeight = (fontSize.value * 1.35f).sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 200,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
