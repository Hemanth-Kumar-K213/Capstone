package com.womensafety.system.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.TrustedContactHomeViewModel

@Composable
fun TCProfileScreen(
    viewModel: TrustedContactHomeViewModel,
    onLogout: () -> Unit
) {
    val state by viewModel.homeState.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadHomeData() }

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
            CircularProgressIndicator(color = GradientRose)
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
            color = GradientRose
        )

        Spacer(modifier = Modifier.height(24.dp))

        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                ProfileField(label = "Name", value = state.profile?.name ?: "—")
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = GlassBorder
                )
                ProfileField(label = "Phone", value = state.profile?.phone ?: "—")
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = GlassBorder
                )
                ProfileField(
                    label = "Connected Users",
                    value = "${state.connectedUsers.size}"
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

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
private fun ProfileField(label: String, value: String) {
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
