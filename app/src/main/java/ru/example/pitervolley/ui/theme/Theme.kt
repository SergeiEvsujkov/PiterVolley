package ru.example.pitervolley.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueLight,
    onPrimaryContainer = BrandBlueDark,
    secondary = BrandOrange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBE3D0),
    onSecondaryContainer = Color(0xFF7A3A0B),
    tertiary = BrandGreen,
    onTertiary = Color.White,
    background = Canvas,
    onBackground = Ink,
    surface = CardSurface,
    onSurface = Ink,
    surfaceVariant = CardMuted,
    onSurfaceVariant = InkMuted,
    outline = Color(0xFFC7D4E6),
    outlineVariant = Color(0xFFE2EAF5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9EC1FF),
    onPrimary = Color(0xFF03183C),
    primaryContainer = Color(0xFF1B3E74),
    onPrimaryContainer = Color(0xFFDCE7FA),
    secondary = Color(0xFFF0A468),
    onSecondary = Color(0xFF3A1B00),
    secondaryContainer = Color(0xFF5A3413),
    onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFF7FD4AE),
    onTertiary = Color(0xFF00281A),
    background = NightCanvas,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightMuted,
    onSurfaceVariant = NightInkMuted,
    outline = Color(0xFF3B4E6B),
    outlineVariant = Color(0xFF22304A),
)

@Composable
fun PiterVolleyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
