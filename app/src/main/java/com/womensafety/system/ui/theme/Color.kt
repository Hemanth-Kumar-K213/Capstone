package com.womensafety.system.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Primary Indigo / Violet / Blue Palette ──────────────────────
// Primary: Indigo
val PrimaryIndigo = Color(0xFF4F46E5)
val PrimaryIndigoLight = Color(0xFF818CF8)
val PrimaryIndigoDark = Color(0xFF3730A3)
val PrimaryIndigoContainer = Color(0xFF312E81)
val OnPrimaryIndigo = Color(0xFFFFFFFF)

// Secondary: Violet
val SecondaryViolet = Color(0xFF7C3AED)
val SecondaryVioletLight = Color(0xFFA78BFA)
val SecondaryVioletDark = Color(0xFF5B21B6)
val SecondaryVioletContainer = Color(0xFF4C1D95)
val OnSecondaryViolet = Color(0xFFFFFFFF)

// Accent: Blue
val AccentBlue = Color(0xFF2563EB)
val AccentBlueLight = Color(0xFF60A5FA)
val AccentBlueDark = Color(0xFF1D4ED8)

// ─── Light Theme Palette ──────────────────────────────────────────
val BackgroundLight = Color(0xFFF6F7FC)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFEEF1F8)
val SurfaceElevatedLight = Color(0xFFFFFFFF)
val OnBackgroundLight = Color(0xFF171B2C)
val OnSurfaceLight = Color(0xFF171B2C)
val OnSurfaceVariantLight = Color(0xFF626A80)
val OutlineLight = Color(0xFFE2E5F0)

// ─── Dark Theme Palette (Deep Indigo/Slate, never pure black) ─────
val BackgroundDark = Color(0xFF171A2B)
val SurfaceDark = Color(0xFF20243A)
val SurfaceVariantDark = Color(0xFF292E46)
val SurfaceElevatedDark = Color(0xFF323854)
val OnBackgroundDark = Color(0xFFF4F5FF)
val OnSurfaceDark = Color(0xFFF4F5FF)
val OnSurfaceVariantDark = Color(0xFFB5BCD2)
val OutlineDark = Color(0xFF383E58)

// ─── M3 Semantic Mappings ─────────────────────────────────────────
val Primary = PrimaryIndigo
val PrimaryLight = PrimaryIndigoLight
val PrimaryDark = PrimaryIndigoDark
val PrimaryContainer = PrimaryIndigoContainer
val OnPrimary = OnPrimaryIndigo
val OnPrimaryContainer = Color(0xFFE0E7FF)

val Secondary = SecondaryViolet
val SecondaryLight = SecondaryVioletLight
val SecondaryDark = SecondaryVioletDark
val SecondaryContainer = SecondaryVioletContainer
val OnSecondary = OnSecondaryViolet
val OnSecondaryContainer = Color(0xFFEDE9FE)

val Tertiary = AccentBlue
val TertiaryContainer = Color(0xFF1E3A8A)
val OnTertiary = Color(0xFFFFFFFF)
val OnTertiaryContainer = Color(0xFFDBEAFE)

// ─── Emergency & Status Colors (Distinct, high-contrast) ──────────
val EmergencyRed = Color(0xFFEF4444)
val EmergencyRedDark = Color(0xFFDC2626)
val EmergencyRedLight = Color(0xFFF87171)
val EmergencyRedContainer = Color(0xFF450A0A)
val OnEmergencyRed = Color(0xFFFFFFFF)

val StatusApproved = Color(0xFF10B981) // Emerald Green
val StatusPending = Color(0xFFF59E0B)  // Amber
val AmberWarning = Color(0xFFF59E0B)   // Safety Amber Warning
val StatusDenied = EmergencyRed        // Distinct Emergency Red
val StatusRevoked = Color(0xFF94A3B8)  // Slate
val StatusExpired = Color(0xFF64748B)

// ─── Error Roles ──────────────────────────────────────────────────
val ErrorColor = EmergencyRed
val ErrorContainerColor = Color(0xFF450A0A)
val OnErrorColor = Color(0xFFFFFFFF)
val OnErrorContainerColor = Color(0xFFFECACA)

// ─── Gradients: Refined Indigo / Violet / Blue ────────────────────
val GradientIndigoStart = Color(0xFF4F46E5)
val GradientIndigoEnd = Color(0xFF7C3AED)
val GradientVioletStart = Color(0xFF7C3AED)
val GradientVioletEnd = Color(0xFF2563EB)
val GradientBlueStart = Color(0xFF2563EB)
val GradientBlueEnd = Color(0xFF4F46E5)

// Preserving legacy token names mapped to the refined modern indigo/violet palette
val GradientAmber = Color(0xFF6366F1)       // Replaced previous orange with vibrant Indigo-600
val GradientGold = Color(0xFFA5B4FC)        // Replaced yellow with soft Indigo-200 accent
val GradientRose = Color(0xFF818CF8)        // Replaced pink with Indigo-400
val GradientMagenta = Color(0xFF7C3AED)     // Violet-600
val GradientPurple = Color(0xFF4F46E5)      // Indigo-600
val GradientDeepPurple = Color(0xFF1E1B4B)  // Deep Indigo-950

// ─── Glass & Border Overlays ──────────────────────────────────────
val GlassWhite = Color(0x14FFFFFF)
val GlassBorder = Color(0x2E818CF8)
val ShimmerHighlight = Color(0x33A5B4FC)
val CardBorderSubtle = Color(0x266366F1)
