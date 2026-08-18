package com.womensafety.system.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.R
import com.womensafety.system.data.model.PermissionState
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.TrustedContactHomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TCHomeScreen(
    viewModel: TrustedContactHomeViewModel,
    onNavigateToAddUser: () -> Unit
) {
    val state by viewModel.homeState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadHomeData() }

    Scaffold(
        containerColor = BackgroundDark,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAddUser,
                icon = { Icon(Icons.Filled.Add, "Add User") },
                text = { Text("ADD USER") },
                containerColor = GradientRose,
                contentColor = OnPrimary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(GradientDeepPurple.copy(alpha = 0.6f), BackgroundDark)
                    )
                )
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Header
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
                        state.profile?.name ?: "Contact",
                        style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 1.sp),
                        fontWeight = FontWeight.Bold,
                        color = GradientRose
                    )
                }
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "CONNECTED USERS",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
                fontWeight = FontWeight.SemiBold,
                color = OnSurfaceVariantDark
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GradientRose)
                }
            } else if (state.connectedUsers.isEmpty()) {
                GlassCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
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
                                Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = OnSurfaceVariantDark.copy(alpha = 0.5f)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No connected users yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnSurfaceVariantDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tap ADD USER to connect with someone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariantDark.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.connectedUsers) { connectedUser ->
                        val statusColor = when (connectedUser.relationship.permissionState) {
                            PermissionState.APPROVED -> StatusApproved
                            PermissionState.PENDING -> StatusPending
                            PermissionState.DENIED -> StatusDenied
                            PermissionState.REVOKED -> StatusRevoked
                            PermissionState.EXPIRED -> StatusExpired
                        }

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
                                        .background(GradientAmber.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = GradientAmber
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        connectedUser.relationship.userName.ifBlank { "Unknown User" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = OnSurfaceDark
                                    )
                                    Text(
                                        connectedUser.relationship.permissionState.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = statusColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
