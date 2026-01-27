package com.wayy.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * MapPulse Ultimate Theme
 * Dark theme with cyberpunk glassmorphism aesthetic
 */
private val DarkColorScheme = darkColorScheme(
    primary = WayyColors.PrimaryLime,
    secondary = WayyColors.PrimaryCyan,
    tertiary = WayyColors.PrimaryPurple,
    background = WayyColors.BgPrimary,
    surface = WayyColors.BgSecondary,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = WayyColors.TextPrimary,
    onSurface = WayyColors.TextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = WayyColors.PrimaryLime,
    secondary = WayyColors.PrimaryCyan,
    tertiary = WayyColors.PrimaryPurple,
    background = WayyColors.BgSecondary,
    surface = WayyColors.BgTertiary,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = WayyColors.TextPrimary,
    onSurface = WayyColors.TextPrimary
)

@Composable
fun WayyTheme(
    darkTheme: Boolean = true, // Always dark for navigation app
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = WayyColors.BgPrimary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WayyTypography,
        content = content
    )
}
