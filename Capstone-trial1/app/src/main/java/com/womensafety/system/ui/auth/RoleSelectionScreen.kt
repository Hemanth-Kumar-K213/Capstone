package com.womensafety.system.ui.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupervisedUserCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.R
import com.womensafety.system.ui.theme.*

@Composable
fun RoleSelectionScreen(
    onUserSelected: () -> Unit,
    onTrustedContactSelected: () -> Unit
) {
    // Staggered animations
    val logoAlpha = remember { Animatable(0f) }
    val card1Alpha = remember { Animatable(0f) }
    val card2Alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, animationSpec = tween(600))
        card1Alpha.animateTo(1f, animationSpec = tween(400))
        card2Alpha.animateTo(1f, animationSpec = tween(400))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        GradientDeepPurple,
                        BackgroundDark
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Trinetra",
                modifier = Modifier
                    .size(100.dp)
                    .alpha(logoAlpha.value)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "TRINETRA",
                style = MaterialTheme.typography.titleLarge.copy(
                    letterSpacing = 3.sp
                ),
                fontWeight = FontWeight.Bold,
                color = GradientGold,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(logoAlpha.value)
            )

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Who are you?",
                style = MaterialTheme.typography.titleMedium,
                color = OnSurfaceVariantDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(logoAlpha.value)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // User Button — Gradient card
            GlassRoleCard(
                icon = Icons.Filled.Person,
                title = "USER",
                subtitle = "I need safety protection",
                gradientColors = listOf(GradientAmber, GradientGold),
                onClick = onUserSelected,
                modifier = Modifier.alpha(card1Alpha.value)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Trusted Contact Button — Gradient card
            GlassRoleCard(
                icon = Icons.Filled.SupervisedUserCircle,
                title = "TRUSTED CONTACT",
                subtitle = "I want to protect someone",
                gradientColors = listOf(GradientRose, GradientMagenta),
                onClick = onTrustedContactSelected,
                modifier = Modifier.alpha(card2Alpha.value)
            )
        }
    }
}

@Composable
private fun GlassRoleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    gradientColors: List<androidx.compose.ui.graphics.Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        GlassBorder,
                        gradientColors.first().copy(alpha = 0.3f)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = GlassWhite
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            gradientColors.first().copy(alpha = 0.15f),
                            gradientColors.last().copy(alpha = 0.05f)
                        )
                    )
                )
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = gradientColors.first()
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        letterSpacing = 2.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = gradientColors.first()
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantDark.copy(alpha = 0.7f)
                )
            }
        }
    }
}
