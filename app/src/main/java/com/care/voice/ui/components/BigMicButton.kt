package com.care.voice.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.care.voice.R

@Composable
fun BigMicButton(
    active: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // лёгкая «дыхательная» анимация масштаба
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

    // если нет второй «активной» картинки, можно оставить одну cloud_idle
    val cloudRes = if (active) R.drawable.cloud_active else R.drawable.cloud_idle

    // лёгкое свечение поверх, когда активно
    val glowOverlay = if (active) {
        Modifier.background(
            brush = Brush.radialGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    Color.Transparent
                )
            )
        )
    } else Modifier

    // неблокирующий ripple (bounded, по прямоугольнику — у PNG форма облака задаётся альфой)
    val ripple = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(220.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                // лёгкая тень под облаком
                shadowElevation = if (active) 18f else 12f
                shape = RoundedCornerShape(36.dp) // сгладим края клика/тени
                clip = false
            }
            .clickable(
                interactionSource = ripple,
                indication = null, // рипл можно отключить, облако и так «дышит»
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // фон — облако с прозрачностью
        Image(
            painter = painterResource(cloudRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // мягкий подсвет-оверлей при активности
        Box(modifier = Modifier.fillMaxSize().then(glowOverlay))

        // иконка микрофона
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .size(48.dp)
        )

        // подпись внутри облака
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .fillMaxWidth(0.8f)
        )
    }
}
