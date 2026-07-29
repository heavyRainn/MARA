package com.care.voice.ui.feature.photo

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.care.voice.brain.vision.VisionUiState
import com.care.voice.ui.components.BigMicButton
import com.care.voice.ui.speak.SpeakViewModel
import com.care.voice.ui.speak.VoiceState
import java.io.File
import java.util.Locale

@Composable
fun PhotoPanel(
    vm: SpeakViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val ui by vm.state
    val voiceState = ui.voiceState
    val stagedPhoto = ui.pendingPhotoUri

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var hasMicPermission by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) vm.onPhotoMicPressed(Locale.forLanguageTag("ru-RU"))
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraUri?.let { vm.stagePhoto(it) }
        }
        pendingCameraUri = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createCameraImageUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    val pickGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) vm.stagePhoto(uri)
    }

    androidx.compose.runtime.LaunchedEffect(context) {
        hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun launchCamera() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            val uri = createCameraImageUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val visionState = ui.visionState
    val visionBusy = visionState == VisionUiState.PreparingImage ||
        visionState == VisionUiState.Analyzing

    val micLabel = when {
        visionBusy -> "Рассматриваю фотографию…"
        stagedPhoto == null -> "Сначала сделайте фото"
        !hasMicPermission -> "Разрешите микрофон"
        voiceState == VoiceState.Listening -> "Слушаю вопрос…"
        voiceState == VoiceState.StartingListening -> "Подключаю микрофон…"
        voiceState == VoiceState.Processing -> "Думаю…"
        voiceState == VoiceState.Speaking -> "Говорю…"
        else -> "Спросите про фото"
    }

    val micActive = stagedPhoto != null && !visionBusy && (
        voiceState == VoiceState.Listening ||
            voiceState == VoiceState.StartingListening
        )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Фото",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xCC1A1228),
            tonalElevation = 2.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (stagedPhoto != null) {
                    AsyncImage(
                        model = stagedPhoto,
                        contentDescription = "Предпросмотр фото",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Сделайте снимок или выберите из галереи",
                            color = Color.White.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = {
                pickGalleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                Text("Галерея", modifier = Modifier.padding(start = 6.dp))
            }

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(3.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = { launchCamera() }, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Rounded.CameraAlt,
                        contentDescription = "Сделать фото",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            FilledTonalButton(
                onClick = { vm.clearStagedPhoto() },
                enabled = stagedPhoto != null,
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Text("Сброс", modifier = Modifier.padding(start = 6.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        BigMicButton(
            active = micActive,
            label = micLabel,
            size = 160.dp,
            onClick = {
                if (stagedPhoto == null) return@BigMicButton
                if (!hasMicPermission) {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    vm.onPhotoMicPressed(Locale.forLanguageTag("ru-RU"))
                }
            },
        )

        if (voiceState == VoiceState.Listening || voiceState == VoiceState.StartingListening) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(6.dp),
                progress = { ((ui.rms + 2f) / 10f).coerceIn(0f, 1f) },
            )
        }

        if (voiceState == VoiceState.Processing) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }

        ui.transientHint?.let { hint ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = hint,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }

        Text(
            text = "Нажмите микрофон и задайте вопрос о снимке",
            color = Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
    }
}

private fun createCameraImageUri(context: android.content.Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File.createTempFile("yasna_", ".jpg", dir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
