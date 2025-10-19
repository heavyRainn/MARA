package com.care.voice.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.care.voice.R

@Composable
fun Backdrop() {
    Box(Modifier.fillMaxSize()) {
        // 1) фото
        Image(
            painter = painterResource(R.drawable.bg_too4a),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // 2) мягкий градиент-скрим, чтобы карточки читались на любом фоне
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.28f),
                        0.35f to Color.Black.copy(alpha = 0.18f),
                        1.0f to Color.Black.copy(alpha = 0.35f),
                    )
                )
        )
    }
}
