package com.care.voice.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@Composable
fun BigMicButton(active: Boolean, onClick: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulse by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulseAnim"
    )
    val scale by animateFloatAsState(if (active) pulse else 1f, tween(250), label = "scaleAnim")

    val gradient = Brush.linearGradient(
        colors = if (active) listOf(Color(0xFF6A11CB), Color(0xFF2575FC))
        else listOf(Color(0xFF2C5364), Color(0xFF203A43), Color(0xFF0F2027))
    )

    Box(
        modifier = Modifier
            .size(220.dp).scale(scale)
            .shadow(if (active) 24.dp else 12.dp, CircleShape, clip = false)
            .clip(CircleShape).background(gradient).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(72.dp))
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)) {
            Text(if (active) "ГОВОРИТЕ" else "ГОВОРИТЬ", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
