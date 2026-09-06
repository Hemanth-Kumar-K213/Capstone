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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.R
import com.womensafety.system.ui.components.PrimaryButton
import com.womensafety.system.ui.components.SecondaryButton
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*

@Composable
fun WelcomeSetupScreen(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
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
            )
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // App Logo
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "Women Safety System Logo",
            modifier = Modifier
                .size(110.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Let's Set Up Your Safety System",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 26.sp,
                lineHeight = 32.sp
            ),
            fontWeight = FontWeight.Bold,
            color = GradientGold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "We'll help you configure location tracking, emergency contacts, and safety preferences in just a few quick steps.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariantDark,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Value Cards
        SetupFeatureCard(
            icon = Icons.Filled.LocationOn,
            iconColor = GradientAmber,
            title = "Real-Time Location Tracking",
            description = "Share your live location automatically when emergency SOS is triggered."
        )

        Spacer(modifier = Modifier.height(14.dp))

        SetupFeatureCard(
            icon = Icons.Filled.ContactPhone,
            iconColor = GradientRose,
            title = "Emergency SMS & Calling",
            description = "Instantly notify parents, friends, or trusted guardians even if they don't have the app."
        )

        Spacer(modifier = Modifier.height(14.dp))

        SetupFeatureCard(
            icon = Icons.Filled.Vibration,
            iconColor = GradientGold,
            title = "Smart SOS & Shake Detection",
            description = "Discreetly trigger emergency mode by shaking your phone when you are in danger."
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Action Buttons
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

@Composable
private fun SetupFeatureCard(
    icon: ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    title: String,
    description: String
) {
    GlassCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
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
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurfaceDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantDark
                )
            }
        }
    }
}
