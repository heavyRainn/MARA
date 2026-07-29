package com.care.voice.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.care.voice.platform.voice.YasnaSpeechLog
import com.care.voice.ui.components.AssistantBubble
import com.care.voice.ui.components.Backdrop
import com.care.voice.ui.components.BigMicButton
import com.care.voice.ui.components.UserBubble
import com.care.voice.ui.components.YasnaBottomBar
import com.care.voice.ui.feature.more.MorePanel
import com.care.voice.ui.feature.photo.PhotoPanel
import com.care.voice.ui.navigation.MainTab
import com.care.voice.ui.speak.SpeakViewModel
import com.care.voice.ui.speak.VoiceState
import com.care.voice.brain.vision.VisionUiState
import java.util.Locale

@Composable
fun YasnaMainScreen(vm: SpeakViewModel = viewModel()) {
    var selectedTab by remember { mutableStateOf(MainTab.Chat) }

    LaunchedEffect(vm) {
        vm.openPhotoPanel.collect {
            selectedTab = MainTab.Photo
        }
    }

    LaunchedEffect(vm) {
        vm.navigateToChat.collect {
            selectedTab = MainTab.Chat
        }
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop()

        Column(Modifier.fillMaxSize()) {
            when (selectedTab) {
                MainTab.Chat -> ChatTabContent(
                    vm = vm,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                MainTab.Photo -> PhotoPanel(
                    vm = vm,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                MainTab.More -> MorePanel(
                    vm = vm,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }

            YasnaBottomBar(
                selected = selectedTab,
                onTabSelected = { selectedTab = it },
            )
        }
    }
}

@Composable
private fun ChatTabContent(
    vm: SpeakViewModel,
    modifier: Modifier = Modifier,
) {
    val permission = Manifest.permission.RECORD_AUDIO
    var hasPermission by remember { mutableStateOf(false) }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        YasnaSpeechLog.d("RECORD_AUDIO permission state=$granted (after request)")
        if (granted) vm.onMicPressed(Locale.forLanguageTag("ru-RU"))
    }

    val context = LocalContext.current
    LaunchedEffect(context, permission) {
        hasPermission = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    val ui by vm.state
    val voiceState = ui.voiceState

    val micLabel = when {
        !hasPermission -> "Разрешите микрофон"
        ui.visionState == VisionUiState.PreparingImage ||
            ui.visionState == VisionUiState.Analyzing -> "Рассматриваю фотографию…"
        voiceState == VoiceState.StartingListening -> "Подключаю микрофон…"
        voiceState == VoiceState.Listening -> "Слушаю… говорите"
        voiceState == VoiceState.Processing -> "Думаю…"
        voiceState == VoiceState.Speaking -> "Говорю… нажмите, чтобы остановить"
        voiceState == VoiceState.FollowUpWindow -> "Можете продолжить…"
        voiceState == VoiceState.Error -> "Ошибка — нажмите, чтобы повторить"
        else -> "Нажмите, чтобы говорить"
    }

    val micActive = voiceState == VoiceState.Listening ||
        voiceState == VoiceState.StartingListening ||
        voiceState == VoiceState.FollowUpWindow

    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BigMicButton(
            active = micActive,
            label = micLabel,
            onClick = {
                YasnaSpeechLog.d("microphone button clicked hasPermission=$hasPermission state=$voiceState")
                if (!hasPermission) {
                    requestPermission.launch(permission)
                } else {
                    vm.onMicPressed(Locale.forLanguageTag("ru-RU"))
                }
            },
        )

        Spacer(Modifier.height(8.dp))

        if (voiceState == VoiceState.Listening || voiceState == VoiceState.StartingListening) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(6.dp),
                progress = { ((ui.rms + 2f) / 10f).coerceIn(0f, 1f) },
            )
            Spacer(Modifier.height(6.dp))
        }

        if (voiceState == VoiceState.Processing ||
            ui.visionState == VisionUiState.PreparingImage ||
            ui.visionState == VisionUiState.Analyzing
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
        }

        ui.transientHint?.let { hint ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    hint,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (ui.finalText.isNotBlank() || ui.attachedPhotoUri != null) {
                UserBubble(
                    text = ui.finalText.trim(),
                    photoUri = ui.attachedPhotoUri,
                    isFirst = ui.assistantText.isBlank(),
                )
            }

            if (ui.assistantText.isNotBlank()) {
                AssistantBubble(
                    text = ui.assistantText,
                    isSpeaking = voiceState == VoiceState.Speaking,
                    onRepeatVoice = { vm.repeatAssistant() },
                    onStopVoice = { vm.stopSpeaking() },
                )
            }

            ui.error?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
