package com.womensafety.system.ui.home

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.R
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.Constants
import com.womensafety.system.viewmodel.UserHomeViewModel

@Composable
fun UserHomeScreen(viewModel: UserHomeViewModel) {
    val state by viewModel.homeState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadHomeData() }

    if (state.isLoading) {
        Box(
            Modifier
                .fillMaxSize()
                .background(BackgroundDark),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = GradientAmber)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GradientDeepPurple.copy(alpha = 0.6f), BackgroundDark)
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // Header with logo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Welcome back,",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariantDark
                )
                Text(
                    state.userProfile?.name ?: "User",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        letterSpacing = 1.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = GradientGold
                )
            }
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Account Status — Glass card
        GlassCard {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(StatusApproved.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = StatusApproved,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        "Account Status",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurfaceVariantDark
                    )
                    Text(
                        "✓ Verified & Active",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusApproved
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Trusted Contacts — Gradient-accent card
        GlassCard(
            accentGradient = Brush.horizontalGradient(
                colors = listOf(
                    GradientRose.copy(alpha = 0.1f),
                    GradientMagenta.copy(alpha = 0.05f)
                )
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(GradientRose.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Contacts,
                            contentDescription = null,
                            tint = GradientRose
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Trusted Contacts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurfaceDark
                        )
                        if (state.trustedContactCount < Constants.MIN_TRUSTED_CONTACTS) {
                            Text(
                                "Add at least ${Constants.MIN_TRUSTED_CONTACTS} contacts",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusPending
                            )
                        }
                    }
                    Text(
                        "${state.trustedContactCount}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = GradientRose
                    )
                }
                if (state.pendingRequestCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = StatusPending
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${state.pendingRequestCount} pending request(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusPending
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Current Safety UID — Gradient accent card
        GlassCard(
            accentGradient = Brush.horizontalGradient(
                colors = listOf(
                    GradientAmber.copy(alpha = 0.1f),
                    GradientGold.copy(alpha = 0.05f)
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "CURRENT SAFETY ID",
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 3.sp
                    ),
                    color = OnSurfaceVariantDark
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    state.currentUid?.uid ?: "Generating…",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        letterSpacing = 4.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = GradientGold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Changes automatically every week",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantDark.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Safety System placeholder
        GlassCard {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(GradientPurple.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = GradientMagenta
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Safety System",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurfaceDark
                )
                Text(
                    "Coming Soon",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariantDark.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Reusable glass-morphism card component with optional gradient accent.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accentGradient: Brush? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(GlassBorder, GlassBorder.copy(alpha = 0.1f))
                ),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceVariantDark
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (accentGradient != null) Modifier.background(accentGradient)
                    else Modifier
                )
        ) {
            content()
        }
    }
}
