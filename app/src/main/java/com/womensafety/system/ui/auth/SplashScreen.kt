package com.womensafety.system.ui.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.R
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    sessionState: AuthViewModel.SessionState,
    onNavigateToRoleSelection: () -> Unit,
    onNavigateToUserHome: () -> Unit,
    onNavigateToWelcomeSetup: () -> Unit,
    onNavigateToTCHome: () -> Unit
) {
    val isDark = LocalDarkTheme.current

    // Logo scale animation
    val logoScale = remember { Animatable(0.6f) }
    // Text fade animation
    val textAlpha = remember { Animatable(0f) }
    // Tagline slide + fade
    val taglineAlpha = remember { Animatable(0f) }
    val taglineOffset = remember { Animatable(24f) }

    LaunchedEffect(Unit) {
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        textAlpha.animateTo(1f, animationSpec = tween(500, easing = EaseOutCubic))
        taglineAlpha.animateTo(1f, animationSpec = tween(400))
        taglineOffset.animateTo(0f, animationSpec = tween(400, easing = EaseOutCubic))
    }

    LaunchedEffect(sessionState) {
        if (!sessionState.isChecking) {
            delay(400)
            when {
                sessionState.isLoggedIn && sessionState.accountType == "USER" -> {
                    if (sessionState.setupCompleted) {
                        onNavigateToUserHome()
                    } else {
                        onNavigateToWelcomeSetup()
                    }
                }
                sessionState.isLoggedIn && sessionState.accountType == "TRUSTED_CONTACT" -> onNavigateToTCHome()
                else -> onNavigateToRoleSelection()
            }
        }
    }

    val bgGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1E1B4B),
                BackgroundDark,
                BackgroundDark
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFEEF2FF),
                BackgroundLight,
                BackgroundLight
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient),
        contentAlignment = Alignment.Center
    ) {
        // Subtle ambient radial glow behind logo
        Box(
            modifier = Modifier
                .size(340.dp)
                .clip(CircleShape)
                .alpha(if (isDark) 0.18f else 0.12f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PrimaryIndigo,
                            SecondaryViolet.copy(alpha = 0.4f),
                            Color.Transparent
                        ),
                        radius = 420f
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Trinetra",
                modifier = Modifier
                    .size(150.dp)
                    .scale(logoScale.value)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // App title
            Text(
                text = "TRINETRA",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 34.sp,
                    letterSpacing = 6.sp,
                    shadow = Shadow(
                        color = PrimaryIndigo.copy(alpha = 0.35f),
                        offset = Offset(0f, 4f),
                        blurRadius = 16f
                    )
                ),
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = if (isDark) Color(0xFFC7D2FE) else PrimaryIndigoDark,
                modifier = Modifier.alpha(textAlpha.value)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Tagline
            Text(
                text = "Women Safety & Emergency Response",
                style = MaterialTheme.typography.bodyMedium.copy(
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .alpha(taglineAlpha.value)
                    .offset(y = taglineOffset.value.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (sessionState.isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
            }
        }
    }
}
