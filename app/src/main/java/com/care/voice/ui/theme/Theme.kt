package com.care.voice.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

// ——— Цветовые схемы ———

// Светлая: теплая бумага и пастель
private val LightColors = lightColorScheme(
    primary = Mulberry,
    onPrimary = Color.White,
    secondary = Fir,
    onSecondary = Color.White,

    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = BubbleUser,
    onSurfaceVariant = Ink,

    secondaryContainer = ChipHeader,
    onSecondaryContainer = Ink,

    error = ErrorRed,
    onError = Color.White,
)

// Тёмная (сохранить настроение, но не «выжигать» контраст)
private val DarkColors = darkColorScheme(
    primary = Mulberry,
    onPrimary = Color.White,
    secondary = Sky,
    onSecondary = Color.Black,

    background = Color(0xFF151515),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF1C1C1C),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF27313A),
    onSurfaceVariant = Color(0xFFEDEDED),

    secondaryContainer = Color(0xFF3A3A3A),
    onSecondaryContainer = Color(0xFFEDEDED),

    error = ErrorRed,
    onError = Color.White,
)

// ——— Типографика (крупнее, мягкая межстрочка) ———

// заменяем AppTypography
private val AppTypography = Typography(
    titleMedium = TextStyle(
        fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = TextStyle(                // основной текст в карточках
        fontSize = 15.sp, lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(               // подписи/вспомогательный
        fontSize = 14.sp, lineHeight = 18.sp
    ),
    labelMedium = TextStyle(              // чипы «Пользователь/Ассистент»
        fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp
    ),
    displaySmall = TextStyle(             // подпись в кнопке микрофона
        fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold
    )
)


// ——— Тема ———

@Composable
fun YasnaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Для стабильного «мультяшного» вида лучше оставить false,
    // но можно включить, если хочется подстраиваться под систему.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
