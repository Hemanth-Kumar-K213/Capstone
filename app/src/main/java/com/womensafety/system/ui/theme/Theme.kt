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
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = PrimaryIndigoDark,
    secondary = SecondaryViolet,
    onSecondary = OnSecondaryViolet,
    secondaryContainer = Color(0xFFF5F3FF),
    onSecondaryContainer = SecondaryVioletDark,
    tertiary = AccentBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E7FF),
    onTertiaryContainer = Color(0xFF312E81),
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = Color(0xFFDCDFEA),
    error = EmergencyRed,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = EmergencyRedDark
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA78BFA), // Lavender primary accent for dark mode
    onPrimary = Color(0xFF1E1035),
    primaryContainer = PrimaryIndigoContainer,
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = Color(0xFFC4B5FD), // Radiant pastel lavender accent
    onSecondary = Color(0xFF2E1065),
    secondaryContainer = SecondaryVioletContainer,
    onSecondaryContainer = Color(0xFFF5F3FF),
    tertiary = AccentBlueLight,
    onTertiary = Color(0xFF0F111E),
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = Color(0xFFE0E7FF),
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = Color(0xFF454C72),
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
