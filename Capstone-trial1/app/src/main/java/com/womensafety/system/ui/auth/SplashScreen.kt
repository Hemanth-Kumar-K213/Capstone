package com.womensafety.system.ui.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
    // Logo scale animation
    val logoScale = remember { Animatable(0.5f) }
    // Text fade animation
    val textAlpha = remember { Animatable(0f) }
    // Tagline slide + fade
    val taglineAlpha = remember { Animatable(0f) }
    val taglineOffset = remember { Animatable(30f) }

    LaunchedEffect(Unit) {
        // Animate logo in with spring
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        // Then fade in title
        textAlpha.animateTo(1f, animationSpec = tween(600, easing = EaseOutCubic))
        // Then slide in tagline
        taglineAlpha.animateTo(1f, animationSpec = tween(500))
        taglineOffset.animateTo(0f, animationSpec = tween(500, easing = EaseOutCubic))
    }

    LaunchedEffect(sessionState) {
        if (!sessionState.isChecking) {
            delay(2000)
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

    // Shimmer animation for the loading indicator
    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by shimmerTransition.animateFloat(
        initialValue = -200f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        GradientDeepPurple,
                        BackgroundDark,
                        BackgroundDark
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Subtle radial glow behind logo
        Box(
            modifier = Modifier
                .size(320.dp)
                .alpha(0.15f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            GradientAmber,
                            GradientRose.copy(alpha = 0.3f),
                            BackgroundDark.copy(alpha = 0f)
                        ),
                        radius = 400f
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Trinetra",
                modifier = Modifier
                    .size(160.dp)
                    .scale(logoScale.value)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // App title with gradient text effect
            Text(
                text = "TRINETRA",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 36.sp,
                    letterSpacing = 6.sp,
                    shadow = Shadow(
                        color = GradientAmber.copy(alpha = 0.4f),
                        offset = Offset(0f, 4f),
                        blurRadius = 12f
                    )
                ),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = GradientGold,
                modifier = Modifier.alpha(textAlpha.value)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tagline
            Text(
                text = "Your safety, our priority",
                style = MaterialTheme.typography.bodyLarge.copy(
                    letterSpacing = 1.sp
                ),
                color = OnSurfaceVariantDark.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .alpha(taglineAlpha.value)
                    .offset(y = taglineOffset.value.dp)
            )

            Spacer(modifier = Modifier.height(56.dp))

            if (sessionState.isChecking) {
                // Custom loading indicator with brand colors
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = GradientAmber,
                    trackColor = GradientDeepPurple,
                    strokeWidth = 3.dp
                )
            }
        }
    }
}
