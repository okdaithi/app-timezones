package com.dg.dualclock.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** App tokens (docs/handoff/design/tokens.json). The app is light; the widget is always dark (SPEC A4). */
object AppTok {
    val Background = Color(0xFFF3EFE6)
    val Text = Color(0xFF16181D)
    val Muted = Color(0xFF5B5F68)
    val Card = Color(0xFFFFFFFF)
    val CardBorder = Color(0xFFDDD6C8)
    val WidgetSurface = Color(0xFF16181D)
    val WidgetText = Color(0xFFF3EFE6)
    val WidgetMuted = Color(0xFFA3A7AF)
    val Track = Color(0xFF2A2E36)
    val Window = Color(0xFF5E8C6A)
    val Perth = Color(0xFFF0A060)
    val Galway = Color(0xFF86B8E6)
    val DotOpen = Color(0xFF9FD3AE)
    val DotClosed = Color(0xFF6B7079)
}

// TODO(font): Instrument Sans (UI), Instrument Serif (headings) and DM Mono (digits) are SIL OFL
// fonts from Google Fonts. Bundle them under res/font and swap these system fallbacks.
val UiFont: FontFamily = FontFamily.SansSerif
val DisplayFont: FontFamily = FontFamily.Serif
val DigitsFont: FontFamily = FontFamily.Monospace

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = DisplayFont, fontSize = 36.sp, color = AppTok.Text),
    titleMedium = TextStyle(fontFamily = UiFont, fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontFamily = UiFont, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = UiFont, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = UiFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
)

@Composable
fun DualClockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = AppTok.Text,
            onPrimary = AppTok.Background,
            background = AppTok.Background,
            onBackground = AppTok.Text,
            surface = AppTok.Card,
            onSurface = AppTok.Text,
            onSurfaceVariant = AppTok.Muted,
            outline = AppTok.CardBorder,
        ),
        typography = AppTypography,
        content = content,
    )
}
