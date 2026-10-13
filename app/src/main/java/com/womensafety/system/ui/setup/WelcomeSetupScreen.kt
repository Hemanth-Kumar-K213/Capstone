package com.womensafety.system.ui.setup

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.components.PrimaryButton
import com.womensafety.system.ui.components.SecondaryButton
import com.womensafety.system.ui.theme.*

@Composable
fun WelcomeSetupScreen(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit
) {
    val isDark = LocalDarkTheme.current
    val bgGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF1E1B4B).copy(alpha = 0.5f), BackgroundDark)
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFEEF2FF), BackgroundLight)
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Trinetra Logo",
                modifier = Modifier.size(96.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Welcome to Trinetra",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "We will help you configure emergency contacts, motion triggers, and GPS monitoring in just a few quick steps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            SetupFeatureCard(
                icon = Icons.Filled.LocationOn,
                iconColor = PrimaryIndigo,
                title = "Real-Time GPS Location",
                description = "Automatically broadcast live coordinates to your emergency circle during alerts."
            )

            Spacer(modifier = Modifier.height(14.dp))

            SetupFeatureCard(
                icon = Icons.Filled.ContactPhone,
                iconColor = SecondaryViolet,
                title = "Emergency SMS & Calling",
                description = "Instantly notify trusted contacts via cellular SMS and auto-dial primary emergency numbers."
            )

            Spacer(modifier = Modifier.height(14.dp))

            SetupFeatureCard(
                icon = Icons.Filled.Vibration,
                iconColor = AccentBlue,
                title = "Discreet Shake Detection",
                description = "Activate emergency SOS covertly by shaking your smartphone in high-danger situations."
            )

            Spacer(modifier = Modifier.height(36.dp))

            PrimaryButton(
                text = "GET STARTED",
                onClick = onGetStarted
            )

            Spacer(modifier = Modifier.height(12.dp))

            SecondaryButton(
                text = "SKIP FOR NOW",
                onClick = onSkip
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SetupFeatureCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String
) {
    AppCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
