package com.womensafety.system.ui.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
            .background(SafetyDarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Trinetra",
                modifier = Modifier
                    .size(92.dp)
                    .alpha(logoAlpha.value)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "TRINETRA",
                style = MaterialTheme.typography.titleLarge.copy(
                    letterSpacing = 3.sp
                ),
                fontWeight = FontWeight.Bold,
                color = SafetyTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(logoAlpha.value)
            )

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = "Choose your account role",
                style = MaterialTheme.typography.titleMedium,
                color = SafetyTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(logoAlpha.value)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // User Role Card
            SafetyRoleCard(
                icon = Icons.Filled.Person,
                title = "USER",
                subtitle = "I want personal safety protection and SOS features",
                onClick = onUserSelected,
                modifier = Modifier.alpha(card1Alpha.value)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Trusted Contact Role Card
            SafetyRoleCard(
                icon = Icons.Filled.SupervisedUserCircle,
                title = "TRUSTED CONTACT",
                subtitle = "I am a guardian monitoring safety of someone",
                onClick = onTrustedContactSelected,
                modifier = Modifier.alpha(card2Alpha.value)
            )
        }
    }
}

@Composable
private fun SafetyRoleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = SafetyCardBackground
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
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SafetyPrimaryBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = SafetyPrimaryBlue
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SafetyTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SafetyTextSecondary
                )
            }
        }
    }
}
