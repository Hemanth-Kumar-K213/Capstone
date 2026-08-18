package com.womensafety.system.ui.profile

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.ui.components.*
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.DateUtils
import com.womensafety.system.viewmodel.UserProfileViewModel

@Composable
fun UserProfileScreen(
    viewModel: UserProfileViewModel,
    onNavigateToChangePhone: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onLogout: () -> Unit
) {
    val state by viewModel.profileState.collectAsState()
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadProfile() }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout", color = OnSurfaceDark) },
            text = { Text("Are you sure you want to logout?", color = OnSurfaceVariantDark) },
            containerColor = SurfaceVariantDark,
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) { Text("Logout", color = StatusDenied) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = OnSurfaceVariantDark)
                }
            }
        )
    }

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
        Text(
            "PROFILE",
            style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 3.sp),
            fontWeight = FontWeight.Bold,
            color = GradientGold
        )

        Spacer(modifier = Modifier.height(24.dp))

        SuccessMessage(state.successMessage)
        if (state.successMessage != null) Spacer(modifier = Modifier.height(8.dp))

        ErrorMessage(state.error)
        if (state.error != null) Spacer(modifier = Modifier.height(8.dp))

        // Profile Info Card
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileInfoRow(label = "Name", value = state.userProfile?.name ?: "—")
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = GlassBorder
                )
                ProfileInfoRow(label = "Email", value = state.userProfile?.primaryEmail ?: "—")
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = GlassBorder
                )
                ProfileInfoRow(label = "Phone", value = state.userProfile?.phone ?: "—")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Trusted Contacts Section
        GlassCard(
            accentGradient = Brush.horizontalGradient(
                colors = listOf(
                    GradientRose.copy(alpha = 0.08f),
                    GradientMagenta.copy(alpha = 0.04f)
                )
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(GradientRose.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Contacts,
                            contentDescription = null,
                            tint = GradientRose,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Trusted Contacts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceDark
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "${state.trustedContactCount}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = GradientRose
                    )
                }

                if (state.trustedContacts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = GlassBorder)
                    Spacer(modifier = Modifier.height(8.dp))

                    state.trustedContacts.forEach { rel ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = OnSurfaceVariantDark
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                rel.trustedContactName.ifBlank { "Unknown Contact" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurfaceDark,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "Approved",
                                modifier = Modifier.size(16.dp),
                                tint = StatusApproved
                            )
                        }
                    }
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${state.pendingRequestCount} pending request(s)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = StatusPending
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onNavigateToContacts,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = GradientRose
                    )
                ) {
                    Icon(
                        Icons.Filled.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ADD CONTACT", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // UID Card
        GlassCard(
            accentGradient = Brush.horizontalGradient(
                colors = listOf(
                    GradientAmber.copy(alpha = 0.08f),
                    GradientGold.copy(alpha = 0.04f)
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "CURRENT SAFETY UID",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp),
                    color = OnSurfaceVariantDark
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    state.currentUid?.uid ?: "Generating…",
                    style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 4.sp),
                    fontWeight = FontWeight.Bold,
                    color = GradientGold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Status: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantDark
                    )
                    Text(
                        if (state.currentUid?.isCurrentlyActive == true) "Active" else "Inactive",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (state.currentUid?.isCurrentlyActive == true) StatusApproved
                        else StatusDenied
                    )
                }
                if (state.currentUid != null) {
                    Text(
                        DateUtils.getTimeUntilExpiry(state.currentUid!!.expiresAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantDark.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        OutlinedButton(
            onClick = onNavigateToChangePhone,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = OnSurfaceDark
            )
        ) {
            Text("CHANGE PHONE NUMBER", fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { viewModel.sendPasswordReset() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = OnSurfaceDark
            )
        ) {
            Text("CHANGE PASSWORD", fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Share UID Button
        Button(
            onClick = {
                val uid = state.currentUid?.uid ?: return@Button
                val shareText = "My current Women Safety UID is: $uid"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(intent, "Share Current UID"))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium,
            enabled = state.currentUid?.isCurrentlyActive == true,
            colors = ButtonDefaults.buttonColors(
                containerColor = GradientAmber
            )
        ) {
            Icon(Icons.Filled.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("SHARE CURRENT UID", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = StatusDenied.copy(alpha = 0.1f)
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                "⚠ Your UID connects Trusted Contacts to your account. Do not share it publicly.",
                style = MaterialTheme.typography.bodySmall,
                color = StatusPending,
                modifier = Modifier.padding(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Logout
        OutlinedButton(
            onClick = { showLogoutDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = StatusDenied
            )
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("LOGOUT", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceVariantDark.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurfaceDark
        )
    }
}
