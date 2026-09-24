package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private fun createDarkColorScheme(accentColor: Color) = darkColorScheme(
    primary = accentColor,
    secondary = accentColor,
    tertiary = ThemePrimaryDark,
    background = ThemeBackground,
    surface = ThemeSurface,
    surfaceVariant = ThemeSurfaceVariant,
    onPrimary = ThemePrimaryDark,
    onSecondary = ThemeBackground,
    onTertiary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = ThemeBackground
)

private fun createLightColorScheme(accentColor: Color) = lightColorScheme(
    primary = accentColor,
    secondary = accentColor,
    tertiary = ThemePrimaryDark,
    background = Color(0xFFF0F0F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE5E5EA),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF13131A),
    onSurface = Color(0xFF13131A),
    onSurfaceVariant = Color(0xFF555566),
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun NSPlayerTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    accentColor: Color = ThemePrimary,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) createDarkColorScheme(accentColor) else createLightColorScheme(accentColor)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

