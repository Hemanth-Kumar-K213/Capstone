package com.womensafety.system.ui.setup



import android.Manifest

import android.content.pm.PackageManager

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

import com.womensafety.system.ui.components.AppCard

import com.womensafety.system.ui.components.PrimaryButton

import com.womensafety.system.ui.components.SecondaryButton

import com.womensafety.system.ui.components.StepIndicator

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

    val isDark = LocalDarkTheme.current

    var permissionTriggerCount by remember { mutableIntStateOf(0) }



    // Build permissions list tailored to current OS version

    val permissionsToRequest = remember {

        buildList {

            add(Manifest.permission.ACCESS_FINE_LOCATION)

            add(Manifest.permission.ACCESS_COARSE_LOCATION)

            add(Manifest.permission.SEND_SMS)

            add(Manifest.permission.CALL_PHONE)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {

                add(Manifest.permission.BLUETOOTH_SCAN)

                add(Manifest.permission.BLUETOOTH_ADVERTISE)

                add(Manifest.permission.BLUETOOTH_CONNECT)

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

                title = "Location Tracking",

                description = "Shares high-precision real-time GPS coordinates with trusted contacts during emergencies.",

                icon = Icons.Filled.LocationOn

            ),

            PermissionItemInfo(

                id = "sms",

                permission = Manifest.permission.SEND_SMS,

                title = "Emergency SMS",

                description = "Dispatches SMS distress alerts containing live coordinates to designated emergency contacts.",

                icon = Icons.Filled.Sms

            ),

            PermissionItemInfo(

                id = "phone",

                permission = Manifest.permission.CALL_PHONE,

                title = "Emergency Calling",

                description = "Enables automatic phone dialer to reach your primary contact immediately during SOS events.",

                icon = Icons.Filled.Phone

            )

        )

    }



    val bgGradient = if (isDark) {

        Brush.verticalGradient(

            colors = listOf(Color(0xFF1E1B4B).copy(alpha = 0.5f), BackgroundDark)

        )

    } else {

        Brush.verticalGradient(

            colors = listOf(Color(0xFFEEF2FF), BackgroundLight)

        )

    }



    Box(

        modifier = Modifier

            .fillMaxSize()

            .background(bgGradient)

    ) {

        Column(

            modifier = Modifier

                .fillMaxSize()

                .padding(24.dp)

                .verticalScroll(rememberScrollState())

        ) {

            Spacer(modifier = Modifier.height(12.dp))



            StepIndicator(currentStep = 1, totalSteps = 4)



            Spacer(modifier = Modifier.height(24.dp))



            Text(

                text = "SAFETY PERMISSIONS",

                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),

                color = MaterialTheme.colorScheme.primary,

                fontWeight = FontWeight.Bold

            )



            Spacer(modifier = Modifier.height(4.dp))



            Text(

                text = "Grant System Permissions",

                style = MaterialTheme.typography.headlineSmall,

                fontWeight = FontWeight.Bold,

                color = MaterialTheme.colorScheme.onBackground

            )



            Spacer(modifier = Modifier.height(8.dp))



            Text(

                text = "These permissions allow the safety engine to transmit coordinates, dispatch alerts, and execute emergency calling when danger occurs.",

                style = MaterialTheme.typography.bodySmall,

                color = MaterialTheme.colorScheme.onSurfaceVariant

            )



            Spacer(modifier = Modifier.height(24.dp))



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



            Spacer(modifier = Modifier.height(20.dp))



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

}



@Composable

private fun PermissionCard(

    item: PermissionItemInfo,

    isGranted: Boolean

) {

    AppCard {

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

                    .background(

                        if (isGranted) StatusApproved.copy(alpha = 0.15f)

                        else PrimaryIndigo.copy(alpha = 0.12f)

                    ),

                contentAlignment = Alignment.Center

            ) {

                Icon(

                    imageVector = item.icon,

                    contentDescription = null,

                    tint = if (isGranted) StatusApproved else PrimaryIndigo,

                    modifier = Modifier.size(24.dp)

                )

            }



            Spacer(modifier = Modifier.width(14.dp))



            Column(modifier = Modifier.weight(1f)) {

                Row(verticalAlignment = Alignment.CenterVertically) {

                    Text(

                        text = item.title,

                        style = MaterialTheme.typography.titleMedium,

                        fontWeight = FontWeight.SemiBold,

                        color = MaterialTheme.colorScheme.onSurface,

                        modifier = Modifier.weight(1f)

                    )



                    PermissionStatusBadge(isGranted = isGranted)

                }



                Spacer(modifier = Modifier.height(4.dp))



                Text(

                    text = item.description,

                    style = MaterialTheme.typography.bodySmall,

                    color = MaterialTheme.colorScheme.onSurfaceVariant

                )

            }

        }

    }

}



@Composable

private fun PermissionStatusBadge(isGranted: Boolean) {

    Surface(

        shape = RoundedCornerShape(10.dp),

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

                text = if (isGranted) "Granted" else "Needed",

                style = MaterialTheme.typography.labelSmall,

                fontWeight = FontWeight.Bold,

                color = if (isGranted) StatusApproved else StatusDenied

            )

        }

    }

}

