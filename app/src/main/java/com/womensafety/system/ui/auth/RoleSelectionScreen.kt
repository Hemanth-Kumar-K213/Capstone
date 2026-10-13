package com.womensafety.system.ui.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupervisedUserCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val isDark = LocalDarkTheme.current

    val logoAlpha = remember { Animatable(0f) }
    val card1Alpha = remember { Animatable(0f) }
    val card2Alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, animationSpec = tween(500))
        card1Alpha.animateTo(1f, animationSpec = tween(400))
        card2Alpha.animateTo(1f, animationSpec = tween(400))
    }

    val bgGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1E1B4B),
                BackgroundDark
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFEEF2FF),
                BackgroundLight
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Logo
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Trinetra",
                modifier = Modifier
                    .size(96.dp)
                    .alpha(logoAlpha.value)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "TRINETRA",
                style = MaterialTheme.typography.titleLarge.copy(
                    letterSpacing = 4.sp
                ),
                fontWeight = FontWeight.ExtraBold,
                color = if (isDark) Color(0xFFC7D2FE) else PrimaryIndigoDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(logoAlpha.value)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Smart Women Safety & Incident Shield",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(logoAlpha.value)
            )

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = "Select your role to continue",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(logoAlpha.value)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // User Role Card
            RoleCard(
                icon = Icons.Filled.Person,
                title = "USER",
                subtitle = "I want active personal safety protection & emergency alerts",
                accentColor = PrimaryIndigo,
                onClick = onUserSelected,
                modifier = Modifier.alpha(card1Alpha.value)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Trusted Contact Role Card
            RoleCard(
                icon = Icons.Filled.SupervisedUserCircle,
                title = "TRUSTED CONTACT / GUARDIAN",
                subtitle = "I want to receive alerts & monitor loved ones during travel",
                accentColor = SecondaryViolet,
                onClick = onTrustedContactSelected,
                modifier = Modifier.alpha(card2Alpha.value)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RoleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    val surfaceColor = if (isDark) SurfaceDark else SurfaceLight
    val borderColor = if (isDark) OutlineDark else OutlineLight

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(borderColor, accentColor.copy(alpha = 0.35f))
                ),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = surfaceColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDark) 0.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = accentColor
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        letterSpacing = 1.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
