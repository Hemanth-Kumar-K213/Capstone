package com.womensafety.system.ui.setup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.womensafety.system.ui.components.PrimaryButton
import com.womensafety.system.ui.components.SecondaryButton
import com.womensafety.system.ui.components.StepIndicator
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.SetupWizardViewModel

data class PermissionItemInfo(
    val id: String,
    val permission: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isRequiredForDevice: Boolean = true
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionsSetupScreen(
    viewModel: SetupWizardViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var permissionTriggerCount by remember { mutableIntStateOf(0) }

    // Build permissions list tailored to current OS version
    val permissionsToRequest = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.CALL_PHONE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val multiplePermissionsState = rememberMultiplePermissionsState(
        permissions = permissionsToRequest
    ) { resultsMap ->
        permissionTriggerCount++
        resultsMap.forEach { (perm, granted) ->
            viewModel.updatePermissionStatus(perm, granted)
        }
    }

    // Permission display items
    val permissionItems = remember(permissionTriggerCount) {
        listOf(
            PermissionItemInfo(
                id = "location",
                permission = Manifest.permission.ACCESS_FINE_LOCATION,
                title = "Location Access",
                description = "Shares your precise real-time location with emergency contacts and trusted contacts during SOS alerts.",
                icon = Icons.Filled.LocationOn
            ),
            PermissionItemInfo(
                id = "sms",
                permission = Manifest.permission.SEND_SMS,
                title = "Send SMS",
                description = "Dispatches emergency text alerts with your live location coordinates to all designated emergency contacts.",
                icon = Icons.Filled.Sms
            ),
            PermissionItemInfo(
                id = "phone",
                permission = Manifest.permission.CALL_PHONE,
                title = "Phone Calling",
                description = "Allows the system to automatically place an emergency phone call to your primary emergency contact.",
                icon = Icons.Filled.Phone
            ),
            PermissionItemInfo(
                id = "notifications",
                permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else "",
                title = "Notifications",
                description = "Displays critical safety service status, active SOS state, and weekly UID rotation updates.",
                icon = Icons.Filled.Notifications,
                isRequiredForDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        GradientDeepPurple.copy(alpha = 0.8f),
                        BackgroundDark,
                        BackgroundDark
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        StepIndicator(currentStep = 1, totalSteps = 4)

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "SAFETY PERMISSIONS",
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
            color = GradientGold,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Allow Crucial Permissions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceDark
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "These permissions allow the app to protect you by sending instant alerts, location updates, and calls when you trigger an emergency.",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceVariantDark
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Cards for each permission
        permissionItems.filter { it.isRequiredForDevice }.forEach { item ->
            val isGranted = if (item.permission.isNotBlank()) {
                ContextCompat.checkSelfPermission(context, item.permission) == PackageManager.PERMISSION_GRANTED
            } else true

            PermissionCard(
                item = item,
                isGranted = isGranted
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Request button
        PrimaryButton(
            text = "GRANT PERMISSIONS",
            onClick = {
                multiplePermissionsState.launchMultiplePermissionRequest()
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Next button (allowed even if some denied)
        SecondaryButton(
            text = "CONTINUE TO CONTACTS",
            onClick = onNext
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionCard(
    item: PermissionItemInfo,
    isGranted: Boolean
) {
    GlassCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted) StatusApproved.copy(alpha = 0.15f)
                        else GradientAmber.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = if (isGranted) StatusApproved else GradientAmber,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceDark,
                        modifier = Modifier.weight(1f)
                    )

                    StatusBadge(isGranted = isGranted)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantDark
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(isGranted: Boolean) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isGranted) StatusApproved.copy(alpha = 0.15f) else StatusDenied.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = if (isGranted) StatusApproved else StatusDenied,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isGranted) "Granted" else "Denied",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isGranted) StatusApproved else StatusDenied
            )
        }
    }
}
