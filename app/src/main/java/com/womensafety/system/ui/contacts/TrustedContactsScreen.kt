package com.womensafety.system.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.data.model.PermissionState
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.Constants
import com.womensafety.system.viewmodel.PermissionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustedContactsScreen(
    viewModel: PermissionViewModel,
    onNavigateToRequests: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadRelationships() }

    val pendingCount = viewModel.getPendingCount()
    val approvedCount = viewModel.getApprovedCount()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GradientDeepPurple.copy(alpha = 0.6f), BackgroundDark)
                )
            )
            .padding(24.dp)
    ) {
        Text(
            "TRUSTED CONTACTS",
            style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 2.sp),
            fontWeight = FontWeight.Bold,
            color = GradientGold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Minimum required: ${Constants.MIN_TRUSTED_CONTACTS}",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariantDark
        )

        if (approvedCount < Constants.MIN_TRUSTED_CONTACTS) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Please add at least ${Constants.MIN_TRUSTED_CONTACTS} Trusted Contacts.",
                style = MaterialTheme.typography.bodySmall,
                color = StatusPending
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pending requests badge
        if (pendingCount > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToRequests,
                colors = CardDefaults.cardColors(
                    containerColor = GradientAmber.copy(alpha = 0.12f)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GradientAmber.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = GradientAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "$pendingCount pending request(s)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        color = GradientGold
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = OnSurfaceVariantDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GradientAmber)
            }
        } else if (state.relationships.isEmpty()) {
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
                            Icons.Filled.Contacts,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = OnSurfaceVariantDark.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No Trusted Contacts yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurfaceVariantDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Share your Safety UID with people you trust.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantDark.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(
                    state.relationships.filter {
                        it.permissionState != PermissionState.PENDING
                    }
                ) { rel ->
                    val statusColor = when (rel.permissionState) {
                        PermissionState.APPROVED -> StatusApproved
                        PermissionState.DENIED -> StatusDenied
                        PermissionState.REVOKED -> StatusRevoked
                        PermissionState.EXPIRED -> StatusExpired
                        PermissionState.PENDING -> StatusPending
                    }
                    var showRevokeDialog by remember { mutableStateOf(false) }
                    val tcName = rel.trustedContactName.ifBlank { "Unknown Contact" }

                    if (showRevokeDialog) {
                        AlertDialog(
                            onDismissRequest = { showRevokeDialog = false },
                            title = { Text("Revoke Access", color = OnSurfaceDark) },
                            text = {
                                Text(
                                    "Are you sure you want to revoke ${tcName}'s access?",
                                    color = OnSurfaceVariantDark
                                )
                            },
                            containerColor = SurfaceVariantDark,
                            confirmButton = {
                                TextButton(onClick = {
                                    showRevokeDialog = false
                                    viewModel.revokeAccess(rel.id)
                                }) {
                                    Text("Revoke", color = StatusDenied)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showRevokeDialog = false }) {
                                    Text("Cancel", color = OnSurfaceVariantDark)
                                }
                            }
                        )
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
                                    .background(GradientRose.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = GradientRose
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    tcName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OnSurfaceDark
                                )
                                Text(
                                    "Status: ${rel.permissionState.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColor
                                )
                            }

                            if (rel.permissionState == PermissionState.APPROVED) {
                                TextButton(
                                    onClick = { showRevokeDialog = true },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = StatusDenied
                                    )
                                ) {
                                    Text("REVOKE", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
