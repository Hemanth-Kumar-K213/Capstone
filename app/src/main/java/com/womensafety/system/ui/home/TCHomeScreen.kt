package com.womensafety.system.ui.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.R
import com.womensafety.system.data.model.PermissionState
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.components.EmptyStateCard
import com.womensafety.system.ui.components.StatusBadge
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
    val isDark = LocalDarkTheme.current
    var userToRemove by remember { mutableStateOf<TrustedContactHomeViewModel.ConnectedUser?>(null) }

    LaunchedEffect(Unit) { viewModel.loadHomeData() }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.loadHomeData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val bgGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF2E1065).copy(alpha = 0.4f), BackgroundDark)
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFF5F3FF), BackgroundLight)
        )
    }

    if (userToRemove != null) {
        val user = userToRemove!!
        val isApproved = user.relationship.permissionState == PermissionState.APPROVED
        val displayName = user.relationship.userName.ifBlank { "Protected User" }

        AlertDialog(
            onDismissRequest = { userToRemove = null },
            icon = {
                Icon(
                    imageVector = if (isApproved) Icons.Filled.PersonRemove else Icons.Filled.Cancel,
                    contentDescription = null,
                    tint = EmergencyRed,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = if (isApproved) "Remove Monitored User?" else "Cancel Connection Request?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = if (isApproved) {
                        "Are you sure you want to stop monitoring $displayName? You will no longer receive their emergency alerts or radar location updates."
                    } else {
                        "Are you sure you want to cancel the pending connection request sent to $displayName?"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
            confirmButton = {
                Button(
                    onClick = {
                        val relId = user.relationship.id
                        userToRemove = null
                        viewModel.removeUser(relId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isApproved) "Remove User" else "Cancel Request",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { userToRemove = null }) {
                    Text(
                        "Keep",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAddUser,
                icon = { Icon(Icons.Filled.Add, "Add User", tint = Color.White) },
                text = { Text("ADD USER", fontWeight = FontWeight.Bold, color = Color.White) },
                containerColor = MaterialTheme.colorScheme.secondary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient)
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Guardian Dashboard,",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        state.profile?.name ?: "Trusted Contact",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "MONITORED PROTECTED USERS",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.secondary,
                        strokeWidth = 3.dp
                    )
                }
            } else if (state.connectedUsers.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Filled.PersonSearch,
                    title = "No Connected Users",
                    description = "Tap 'ADD USER' to enter a family member or friend's Safety UID and monitor their real-time trips."
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.connectedUsers, key = { it.relationship.id }) { connectedUser ->
                        val statusColor = when (connectedUser.relationship.permissionState) {
                            PermissionState.APPROVED -> StatusApproved
                            PermissionState.PENDING -> StatusPending
                            PermissionState.DENIED -> StatusDenied
                            PermissionState.REVOKED -> StatusRevoked
                            PermissionState.EXPIRED -> StatusExpired
                        }

                        AppCard {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = connectedUser.relationship.userName.ifBlank { "Protected User" },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        StatusBadge(
                                            text = connectedUser.relationship.permissionState.name,
                                            color = statusColor
                                        )
                                    }

                                    // Remove / Unlink action button
                                    IconButton(
                                        onClick = { userToRemove = connectedUser },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(EmergencyRed.copy(alpha = 0.12f))
                                    ) {
                                        Icon(
                                            Icons.Filled.DeleteOutline,
                                            contentDescription = "Remove User",
                                            tint = EmergencyRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                if (connectedUser.relationship.permissionState == PermissionState.APPROVED) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = { onNavigateToLiveMap(connectedUser.relationship.userId) },
                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.LocationOn,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("VIEW LIVE RADAR LOCATION", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                    }
                                } else if (connectedUser.relationship.permissionState == PermissionState.PENDING) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    Spacer(modifier = Modifier.height(12.dp))

                                    OutlinedButton(
                                        onClick = { userToRemove = connectedUser },
                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyRed),
                                        border = BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = EmergencyRed
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("CANCEL REQUEST", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EmergencyRed)
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
