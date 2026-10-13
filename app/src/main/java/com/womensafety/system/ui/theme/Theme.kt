package com.womensafety.system.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalDarkTheme = staticCompositionLocalOf { false }

private val LightColorScheme = lightColorScheme(
    primary = PrimaryIndigo,
    onPrimary = OnPrimaryIndigo,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = PrimaryIndigoDark,
    secondary = SecondaryViolet,
    onSecondary = OnSecondaryViolet,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = SecondaryVioletDark,
    tertiary = AccentBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDBEAFE),
    onTertiaryContainer = Color(0xFF1E40AF),
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = Color(0xFFD1D5DB),
    error = EmergencyRed,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = EmergencyRedDark
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA5B4FC), // Primary accent for dark
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = PrimaryIndigoContainer,
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFFC4B5FD), // Secondary accent for dark
    onSecondary = Color(0xFF2E1065),
    secondaryContainer = SecondaryVioletContainer,
    onSecondaryContainer = Color(0xFFEDE9FE),
    tertiary = AccentBlueLight,
    onTertiary = Color(0xFF0F172A),
    tertiaryContainer = Color(0xFF1E3A8A),
    onTertiaryContainer = Color(0xFFDBEAFE),
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = Color(0xFF475569),
    error = EmergencyRed,
    onError = Color.White,
    errorContainer = EmergencyRedContainer,
    onErrorContainer = Color(0xFFFECACA)
)

@Composable
fun WomenSafetyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val bgArgb = (if (darkTheme) BackgroundDark else BackgroundLight).toArgb()
            window.statusBarColor = bgArgb
            window.navigationBarColor = bgArgb
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
