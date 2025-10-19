package com.care.voice.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/* ================= USER ================= */

@Composable
fun UserBubble(
    text: String,
    isFirst: Boolean
) {
    if (text.isBlank()) return

    var menuExpanded by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf(16.sp) }
    val clipboard = LocalClipboardManager.current
    val scroll = rememberScrollState()

    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1f)) // выравниваем пузырь вправо
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.92f) // немного уже, чтобы читалось как «свой»
                .clip(RoundedCornerShape(18.dp))
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { /* no-op */ },
                        label = {
                            Text(
                                "Пользователь" + if (isFirst) " • первая фраза" else "",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Меню")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Скопировать") }, onClick = {
                            clipboard.setText(AnnotatedString(text)); menuExpanded = false
                        })
                        DropdownMenuItem(text = { Text("Крупнее") }, onClick = {
                            fontSize = (fontSize.value + 2f).sp; menuExpanded = false
                        })
                        DropdownMenuItem(text = { Text("Мельче") }, onClick = {
                            fontSize = maxOf(12f, fontSize.value - 2f).sp; menuExpanded = false
                        })
                    }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = text,
                    fontSize = fontSize,
                    lineHeight = (fontSize.value * 1.3f).sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                        .verticalScroll(scroll),
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Visible
                )
            }
        }
    }
}

/* ================= ASSISTANT ================= */

@Composable
fun AssistantBubble(
    text: String,
    isSpeaking: Boolean,
    onRepeatVoice: (() -> Unit)? = null,
    onStopVoice: (() -> Unit)? = null
) {
    if (text.isBlank()) return

    var menuExpanded by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf(16.sp) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val collapsedMax = 160.dp
    val expandedMax  = 420.dp
    val stopSlotWidth = 64.dp       // фиксированное место под кнопку «Стоп»

    Row(Modifier.fillMaxWidth()) {
        Surface(
            tonalElevation = 2.dp,
            shadowElevation = 0.dp,
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(18.dp))
        ) {
            Column(Modifier.padding(16.dp)) {

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // слева — плашка «Ассистент»
                    AssistChip(
                        onClick = { /* no-op */ },
                        label = {
                            Text(
                                "Ассистент",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )

                    Spacer(Modifier.weight(1f))

                    // центр — зарезервированное место под кнопку «Стоп»
                    Box(
                        modifier = Modifier.width(stopSlotWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSpeaking && onStopVoice != null) {
                            TextButton(
                                onClick = onStopVoice,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) { Text("Стоп") }
                        }
                    }

                    // справа — меню
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Меню")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(if (expanded) "Свернуть" else "Развернуть") },
                            onClick = { expanded = !expanded; menuExpanded = false }
                        )
                        DropdownMenuItem(text = { Text("Прокрутить вверх") }, onClick = {
                            scope.launch { scrollState.animateScrollTo(0) }; menuExpanded = false
                        })
                        DropdownMenuItem(text = { Text("Прокрутить вниз") }, onClick = {
                            scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }; menuExpanded = false
                        })
                        DropdownMenuItem(text = { Text("Скопировать") }, onClick = {
                            clipboard.setText(AnnotatedString(text)); menuExpanded = false
                        })
                        DropdownMenuItem(text = { Text("Крупнее") }, onClick = {
                            fontSize = (fontSize.value + 2f).sp; menuExpanded = false
                        })
                        DropdownMenuItem(text = { Text("Мельче") }, onClick = {
                            fontSize = maxOf(12f, fontSize.value - 2f).sp; menuExpanded = false
                        })
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

                Spacer(Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (expanded) expandedMax else collapsedMax)
                        .animateContentSize()
                        .verticalScroll(scrollState)
                        .clickable { expanded = !expanded }
                ) {
                    Text(
                        text = text,
                        fontSize = fontSize,
                        lineHeight = (fontSize.value * 1.35f).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Visible
                    )

                    // градиент-подсказка, когда есть что листать и блок свёрнут
                    val canScrollDown = scrollState.value < scrollState.maxValue
                    if (!expanded && canScrollDown) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .drawWithContent {
                                    drawContent()
                                    val h = size.height
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            0.7f to Color(0xAAFFFFFF),
                                            1f to Color.White
                                        ),
                                        topLeft = Offset(0f, h - 120f),
                                        size = Size(size.width, 120f),
                                        alpha = 0.9f
                                    )
                                }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}
