package com.care.voice.ui.components

import androidx.compose.animation.animateContentSize
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
    var fontSize by remember { mutableStateOf(15.sp) }
    val clipboard = LocalClipboardManager.current
    val scroll = rememberScrollState()

    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1f)) // выравниваем вправо
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 1.dp,
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
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
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                    Spacer(Modifier.weight(1f))
                    FilledTonalIconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Меню")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Скопировать") }, onClick = {
                            clipboard.setText(AnnotatedString(text)); menuExpanded = false
                        })
                        DropdownMenuItem(text = { Text("Крупнее") }, onClick = {
                            fontSize = (fontSize.value + 1f).sp; menuExpanded = false
                        })
                        DropdownMenuItem(text = { Text("Мельче") }, onClick = {
                            fontSize = maxOf(12f, fontSize.value - 1f).sp; menuExpanded = false
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
    onStopVoice: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (text.isBlank()) return

    var menuExpanded by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf(15.sp) }
    val clipboard = LocalClipboardManager.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Row(modifier = modifier.fillMaxWidth()) {
        Surface(
            tonalElevation = 1.dp,
            shadowElevation = 0.dp,
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                // ВАЖНО: НЕ растягиваем колонку на всю высоту
            ) {
                // ─── Шапка карточки ────────────────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { /* no-op */ },
                        label = { Text("Ассистент", style = MaterialTheme.typography.labelMedium) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )

                    Spacer(Modifier.weight(1f))

                    // фиксированное место под кнопку «Стоп», чтобы верстка не прыгала
                    Box(
                        modifier = Modifier.width(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSpeaking && onStopVoice != null) {
                            FilledTonalButton(
                                onClick = onStopVoice,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Стоп") }
                        }
                    }

                    FilledTonalIconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Меню")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
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
                            fontSize = (fontSize.value + 1f).sp; menuExpanded = false
                        })
                        DropdownMenuItem(text = { Text("Мельче") }, onClick = {
                            fontSize = maxOf(12f, fontSize.value - 1f).sp; menuExpanded = false
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

                // ─── Контент: растем до доступной высоты, но не тянемся насильно ───
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)   // 🔑 подгон по контенту; если много — ограничится доступной высотой
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = text,
                        fontSize = fontSize,
                        lineHeight = (fontSize.value * 1.33f).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

