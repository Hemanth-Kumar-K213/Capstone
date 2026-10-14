package com.womensafety.system.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Primary Lavender / Violet / Purple Accent Palette ────────────
// Primary: Refined Vibrant Violet
val PrimaryIndigo = Color(0xFF7C3AED)          // Violet 600 - rich, elegant, professional
val PrimaryIndigoLight = Color(0xFFA78BFA)     // Violet 400 - soft lavender violet
val PrimaryIndigoDark = Color(0xFF5B21B6)      // Violet 800 - deep regal violet
val PrimaryIndigoContainer = Color(0xFF2E1065) // Deep violet container
val OnPrimaryIndigo = Color(0xFFFFFFFF)

// Secondary: Radiant Lavender Orchid
val SecondaryViolet = Color(0xFF8B5CF6)        // Violet 500 - luminous lavender accent
val SecondaryVioletLight = Color(0xFFC4B5FD)   // Lavender 300 - delicate pastel lavender
val SecondaryVioletDark = Color(0xFF6D28D9)    // Violet 700 - saturated violet
val SecondaryVioletContainer = Color(0xFF3B1378)
val OnSecondaryViolet = Color(0xFFFFFFFF)

// Accent: Soft Indigo / Electric Iris
val AccentBlue = Color(0xFF6366F1)             // Indigo 500
val AccentBlueLight = Color(0xFF818CF8)        // Indigo 400
val AccentBlueDark = Color(0xFF4338CA)         // Indigo 700

// ─── Light Theme Palette (Soft, pristine lavender-tinted surfaces) ──
val BackgroundLight = Color(0xFFF7F8FC)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFEEF0FA)
val SurfaceElevatedLight = Color(0xFFFFFFFF)
val OnBackgroundLight = Color(0xFF141726)
val OnSurfaceLight = Color(0xFF141726)
val OnSurfaceVariantLight = Color(0xFF5E657E)
val OutlineLight = Color(0xFFE2E5F2)

// ─── Dark Theme Palette (Deep indigo/violet night, never flat black) ─
val BackgroundDark = Color(0xFF121424)         // Deep midnight with violet undertone
val SurfaceDark = Color(0xFF1B1E34)            // Rich dark navy-violet surface
val SurfaceVariantDark = Color(0xFF252945)     // Elevated surface layer
val SurfaceElevatedDark = Color(0xFF2E3355)    // Floating card layer
val OnBackgroundDark = Color(0xFFF5F7FF)
val OnSurfaceDark = Color(0xFFF5F7FF)
val OnSurfaceVariantDark = Color(0xFFA8B0CA)
val OutlineDark = Color(0xFF353B5C)

// ─── M3 Semantic Mappings ─────────────────────────────────────────
val Primary = PrimaryIndigo
val PrimaryLight = PrimaryIndigoLight
val PrimaryDark = PrimaryIndigoDark
val PrimaryContainer = PrimaryIndigoContainer
val OnPrimary = OnPrimaryIndigo
val OnPrimaryContainer = Color(0xFFEDE9FE)

val Secondary = SecondaryViolet
val SecondaryLight = SecondaryVioletLight
val SecondaryDark = SecondaryVioletDark
val SecondaryContainer = SecondaryVioletContainer
val OnSecondary = OnSecondaryViolet
val OnSecondaryContainer = Color(0xFFF5F3FF)

val Tertiary = AccentBlue
val TertiaryContainer = Color(0xFF1E1B4B)
val OnTertiary = Color(0xFFFFFFFF)
val OnTertiaryContainer = Color(0xFFE0E7FF)

// ─── Emergency & Status Colors (Crisp, high-contrast, accessible) ──
val EmergencyRed = Color(0xFFEF4444)           // High alert safety red
val EmergencyRedDark = Color(0xFFDC2626)
val EmergencyRedLight = Color(0xFFF87171)
val EmergencyRedContainer = Color(0xFF450A0A)
val OnEmergencyRed = Color(0xFFFFFFFF)

val StatusApproved = Color(0xFF10B981)         // Emerald safe state
val StatusPending = Color(0xFFF59E0B)          // Amber caution state
val AmberWarning = Color(0xFFF59E0B)           // Safety Amber Warning
val AmberOrange = Color(0xFFF59E0B)            // Warm Amber Orange
val CoralRed = Color(0xFFEF4444)               // Vivid Coral Red
val DeepCyan = Color(0xFF0EA5E9)               // Deep Cyan / Sky Accent
val StatusDenied = EmergencyRed
val StatusRevoked = Color(0xFF94A3B8)          // Slate neutral
val StatusExpired = Color(0xFF64748B)

// ─── Error Roles ──────────────────────────────────────────────────
val ErrorColor = EmergencyRed
val ErrorContainerColor = Color(0xFF450A0A)
val OnErrorColor = Color(0xFFFFFFFF)
val OnErrorContainerColor = Color(0xFFFECACA)

// ─── Gradients: Refined Lavender / Violet / Purple ────────────────
val GradientIndigoStart = Color(0xFF7C3AED)
val GradientIndigoEnd = Color(0xFF6366F1)
val GradientVioletStart = Color(0xFF8B5CF6)
val GradientVioletEnd = Color(0xFFA78BFA)
val GradientBlueStart = Color(0xFF6366F1)
val GradientBlueEnd = Color(0xFF7C3AED)

// Preserving legacy token names mapped to the refined modern palette
val GradientAmber = Color(0xFF7C3AED)
val GradientGold = Color(0xFFC4B5FD)
val GradientRose = Color(0xFFA78BFA)
val GradientMagenta = Color(0xFF9333EA)
val GradientPurple = Color(0xFF7C3AED)
val GradientDeepPurple = Color(0xFF1E1035)

// ─── Glass & Border Overlays ──────────────────────────────────────
val GlassWhite = Color(0x18FFFFFF)
val GlassBorder = Color(0x28A78BFA)
val ShimmerHighlight = Color(0x30C4B5FD)
val CardBorderSubtle = Color(0x207C3AED)

