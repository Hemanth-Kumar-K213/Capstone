package com.womensafety.system.ui.safety

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.ui.theme.*

@Composable
fun SafetyPlaceholderScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GradientDeepPurple.copy(alpha = 0.6f), BackgroundDark)
                )
            )
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(GradientPurple.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Shield,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = GradientMagenta
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "SAFETY SYSTEM",
            style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 3.sp),
            fontWeight = FontWeight.Bold,
            color = GradientGold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Coming Soon",
            style = MaterialTheme.typography.titleLarge,
            color = GradientRose,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Real-time safety monitoring, GPS tracking, SOS alerts, and more will be available in a future update.",
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariantDark.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}
