package com.womensafety.system.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.data.model.PermissionState
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.components.EmptyStateCard
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

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.loadRelationships()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val pendingCount = viewModel.getPendingCount()
    val approvedCount = viewModel.getApprovedCount()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(20.dp)
    ) {
        // Top Header with Refresh Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "TRUSTED CONTACTS",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Minimum required: ${Constants.MIN_TRUSTED_CONTACTS} trusted guardians",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { viewModel.loadRelationships() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Refresh Contacts",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (approvedCount < Constants.MIN_TRUSTED_CONTACTS) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = AmberWarning.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Please pair at least ${Constants.MIN_TRUSTED_CONTACTS} Trusted Contacts for full protection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AmberWarning,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        val pendingRequests = state.relationships.filter { it.permissionState == PermissionState.PENDING }
        val approvedContacts = state.relationships.filter { it.permissionState == PermissionState.APPROVED }
        val otherContacts = state.relationships.filter {
            it.permissionState != PermissionState.PENDING && it.permissionState != PermissionState.APPROVED
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
            }
        } else if (state.relationships.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyStateCard(
                    icon = Icons.Filled.Contacts,
                    title = "No Trusted Contacts Yet",
                    description = "Share your Safety UID with people you trust. When they send a request, it will appear right here for approval."
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // ─── 1. PENDING REQUESTS SECTION (HIGH VISIBILITY) ───
                if (pendingRequests.isNotEmpty()) {
                    item {
                        Surface(
                            color = AmberWarning.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.NotificationsActive,
                                    contentDescription = null,
                                    tint = AmberWarning,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "PENDING CONNECTION REQUESTS (${pendingRequests.size})",
                                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = AmberWarning
                                )
                            }
                        }
                    }

                    items(pendingRequests) { rel ->
                        val contactName = rel.trustedContactName.ifBlank { "Trusted Contact" }
                        AppCard(
                            modifier = Modifier.fillMaxWidth(),
                            accentGradient = Brush.horizontalGradient(
                                colors = listOf(AmberWarning.copy(alpha = 0.18f), AmberWarning.copy(alpha = 0.04f))
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(AmberWarning.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.PersonAdd,
                                            contentDescription = null,
                                            tint = AmberWarning,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            contactName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (rel.trustedContactPhone.isNotBlank()) {
                                            Text(
                                                "Phone: ${rel.trustedContactPhone}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            "Requested pairing to monitor safety",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AmberWarning,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.approveRequest(rel.id) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = StatusApproved)
                                    ) {
                                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("ALLOW", fontWeight = FontWeight.Bold, color = OnPrimary)
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.denyRequest(rel.id) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyRed),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.6f))
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("DENY", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // ─── 2. ACTIVE APPROVED GUARDIANS ───────────────────
                if (approvedContacts.isNotEmpty()) {
                    item {
                        Text(
                            "ACTIVE GUARDIANS (${approvedContacts.size})",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    items(approvedContacts) { rel ->
                        var showRevokeDialog by remember { mutableStateOf(false) }
                        val tcName = rel.trustedContactName.ifBlank { "Guardian Contact" }

                        if (showRevokeDialog) {
                            AlertDialog(
                                onDismissRequest = { showRevokeDialog = false },
                                title = {
                                    Text(
                                        "Revoke Access",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                text = {
                                    Text(
                                        "Are you sure you want to revoke ${tcName}'s access to your real-time safety status?",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.surface,
                                confirmButton = {
                                    TextButton(onClick = {
                                        showRevokeDialog = false
                                        viewModel.revokeAccess(rel.id)
                                    }) {
                                        Text("Revoke Access", color = EmergencyRed, fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showRevokeDialog = false }) {
                                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }

                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(StatusApproved.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.VerifiedUser,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = StatusApproved
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        tcName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (rel.trustedContactPhone.isNotBlank()) {
                                        Text(
                                            "Phone: ${rel.trustedContactPhone}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Surface(
                                        color = StatusApproved.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "MONITORING ACTIVE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StatusApproved,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = { showRevokeDialog = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = EmergencyRed)
                                ) {
                                    Text("REVOKE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // ─── 3. OTHER / REVOKED / INACTIVE CONTACTS ─────────
                if (otherContacts.isNotEmpty()) {
                    item {
                        Text(
                            "INACTIVE CONTACTS (${otherContacts.size})",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    items(otherContacts) { rel ->
                        val tcName = rel.trustedContactName.ifBlank { "Contact" }
                        val statusColor = when (rel.permissionState) {
                            PermissionState.DENIED -> EmergencyRed
                            PermissionState.REVOKED -> MaterialTheme.colorScheme.outline
                            PermissionState.EXPIRED -> AmberWarning
                            else -> MaterialTheme.colorScheme.outline
                        }

                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.PersonOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        tcName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    Surface(
                                        color = statusColor.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            rel.permissionState.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = statusColor,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteRelationship(rel.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.DeleteOutline,
                                            contentDescription = "Remove Contact",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(20.dp)
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
}
