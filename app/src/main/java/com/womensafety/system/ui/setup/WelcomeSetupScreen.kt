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
import androidx.compose.foundation.shape.RoundedCornerShape
import com.womensafety.system.R
import com.womensafety.system.ui.components.*
import com.womensafety.system.ui.theme.*

@Composable
fun WelcomeSetupScreen(
    onGetStarted: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SafetyDarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // App Logo
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "Trinetra Logo",
            modifier = Modifier
                .size(92.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Welcome to Personal Safety",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 26.sp,
                lineHeight = 32.sp
            ),
            fontWeight = FontWeight.Bold,
            color = SafetyTextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Set up critical safety features so you're prepared in an emergency. You can change these anytime in Your info.",
            style = MaterialTheme.typography.bodyMedium,
            color = SafetyTextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Value Cards
        SetupFeatureCard(
            icon = Icons.Filled.LocationOn,
            iconColor = SafetyPrimaryBlue,
            title = "Real-Time Location Tracking",
            description = "Share your live location automatically when emergency SOS is triggered."
        )

        Spacer(modifier = Modifier.height(14.dp))

        SetupFeatureCard(
            icon = Icons.Filled.ContactPhone,
            iconColor = SafetyPrimaryBlue,
            title = "Emergency Contacts",
            description = "Instantly notify parents, friends, or trusted guardians when help is needed."
        )

        Spacer(modifier = Modifier.height(14.dp))

        SetupFeatureCard(
            icon = Icons.Filled.Vibration,
            iconColor = SafetyPrimaryBlue,
            title = "Smart SOS & Shake Detection",
            description = "Discreetly trigger emergency alerts by shaking your phone."
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Action Buttons
        SafetyPillButton(
            text = "Set Up Now",
            onClick = onGetStarted,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        SafetyOutlinedPillButton(
            text = "Not Now",
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth().height(48.dp)
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
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SafetyCardBackground),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SafetyTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = SafetyTextSecondary
                )
            }
        }
    }
}
