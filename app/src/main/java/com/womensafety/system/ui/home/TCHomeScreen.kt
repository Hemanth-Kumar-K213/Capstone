package com.womensafety.system.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onNavigateToAddUser: () -> Unit,
    onNavigateToLiveMap: (String) -> Unit = {}
) {
    val state by viewModel.homeState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadHomeData() }

    Scaffold(
        containerColor = SafetyDarkBackground,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAddUser,
                icon = { Icon(Icons.Filled.Add, "Add User") },
                text = { Text("Add User") },
                containerColor = SafetyPrimaryBlue,
                contentColor = SafetyOnPrimaryBlue
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SafetyDarkBackground)
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
                        "Guardian Dashboard",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SafetyTextSecondary
                    )
                    Text(
                        state.profile?.name ?: "Contact",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = SafetyTextPrimary
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
                "Connected Users",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SafetyTextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SafetyPrimaryBlue)
                }
            } else if (state.connectedUsers.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SafetyCardBackground),
                    modifier = Modifier.fillMaxWidth()
                ) {
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
                                .background(SafetyPrimaryBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = SafetyPrimaryBlue
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No connected users yet.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = SafetyTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tap Add User below to connect with someone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafetyTextSecondary
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
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
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

                                if (connectedUser.relationship.permissionState == PermissionState.APPROVED) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = GlassBorder)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = { onNavigateToLiveMap(connectedUser.relationship.userId) },
                                        modifier = Modifier.fillMaxWidth().height(42.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GradientAmber),
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Icon(
                                            Icons.Filled.LocationOn,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("VIEW LIVE LOCATION", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
