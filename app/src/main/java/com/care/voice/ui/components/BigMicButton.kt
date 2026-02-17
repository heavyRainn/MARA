package com.care.voice.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.care.voice.R

@Composable
fun BigMicButton(
    active: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp
) {
    // лёгкое «дыхание» тучки
    val infinite = rememberInfiniteTransition(label = "cloudPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.97f,
        targetValue  = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAnim"
    )
    val scale by animateFloatAsState(
        targetValue = if (active) pulse else 1f,
        animationSpec = tween(220),
        label = "scaleAnim"
    )

    val interaction = MutableInteractionSource()

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {           // без тени/фона — контейнер прозрачный
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // сама тучка (PNG с альфой), без дополнительного фона
        Image(
            painter = painterResource(if (active) R.drawable.cloud_active else R.drawable.cloud_idle),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // лёгкий glow при активной записи
        if (active) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // иконка микрофона (без отдельной тёмной плашки — просили максимально «чисто»)
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.Center)
                .size(size * 0.22f)
        )

        // подпись внутри тучки — крупнее и не вылезает
        Text(
            text = label,
            color = Color.White,
            fontSize = 16.sp,                // ← крупнее
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .fillMaxWidth(0.82f)
        )
    }
}
