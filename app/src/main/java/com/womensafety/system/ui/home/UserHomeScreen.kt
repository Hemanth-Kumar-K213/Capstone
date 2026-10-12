package com.womensafety.system.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.data.model.SafetyMode
import com.womensafety.system.data.model.UserLocation
import com.womensafety.system.data.model.SOSTriggerMethod
import com.womensafety.system.data.model.SOSStatus
import com.womensafety.system.manager.JourneyState
import com.womensafety.system.manager.SafetyMonitoringManager
import com.womensafety.system.manager.TravelModeManager
import com.womensafety.system.service.LocationTrackingService
import com.womensafety.system.ui.components.*
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.Constants
import com.womensafety.system.viewmodel.UserHomeViewModel
import com.womensafety.system.viewmodel.SOSViewModel
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.womensafety.system.data.repository.LocationRepository
import com.womensafety.system.data.repository.UserRepository
import com.womensafety.system.manager.TrinetraLocationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun UserHomeScreen(
    viewModel: UserHomeViewModel,
    sosViewModel: SOSViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateToLocationSettings: () -> Unit = {},
    onNavigateToShakeSettings: () -> Unit = {},
    onNavigateToSafety: () -> Unit = {},
    onNavigateToAlert: (String) -> Unit = {},
    onNavigateToTravelMode: () -> Unit = {},
    onNavigateToLiveMap: (String) -> Unit = {}
) {
    val state by viewModel.homeState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadHomeData() }

    // Launcher for runtime location permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            LocationTrackingService.startTracking(context)
            val authUserId = state.userProfile?.authUserId ?: ""
            if (authUserId.isNotBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    val fix = TrinetraLocationManager.getImmediateLocationFix(context, authUserId)
                    if (fix != null) {
                        val locRepo = LocationRepository()
                        locRepo.saveLocation(authUserId, fix)
                        locRepo.saveUidLocation(authUserId, fix)
                        val uUid = state.currentUid?.uid ?: state.userProfile?.currentWeeklyUid ?: ""
                        if (uUid.isNotBlank()) {
                            locRepo.saveUidLocation(uUid, fix)
                        }
                    }
                    UserRepository().updateUserProfile(authUserId, mapOf("locationTrackingEnabled" to true))
                }
            }
        }
    }

    LaunchedEffect(state.userProfile?.authUserId, state.currentUid?.uid) {
        val uid = state.currentUid?.uid?.ifBlank { state.userProfile?.currentWeeklyUid ?: "" } ?: ""
        if (uid.isNotBlank()) {
            LocationTrackingService.setTargetUid(uid, state.currentUid?.userName ?: state.userProfile?.name ?: "")
        }
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        } else {
            if (!LocationTrackingService.isRunning) {
                LocationTrackingService.startTracking(context)
            }
            val authUserId = state.userProfile?.authUserId ?: ""
            if (authUserId.isNotBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    val fix = TrinetraLocationManager.getImmediateLocationFix(context, authUserId)
                    if (fix != null) {
                        val locRepo = LocationRepository()
                        locRepo.saveLocation(authUserId, fix)
                        locRepo.saveUidLocation(authUserId, fix)
                        if (uid.isNotBlank()) {
                            locRepo.saveUidLocation(uid, fix)
                        }
                    }
                    UserRepository().updateUserProfile(authUserId, mapOf("locationTrackingEnabled" to true))
                }
            }
        }
    }

    var showSOSConfirmDialog by remember { mutableStateOf(false) }
    var sosConfirmCountdown by remember { mutableIntStateOf(3) }
    val activeSOSEvent by sosViewModel.activeSOSEvent.collectAsState()

    LaunchedEffect(activeSOSEvent) {
        if (activeSOSEvent != null && activeSOSEvent!!.status == SOSStatus.ACTIVE) {
            onNavigateToAlert(activeSOSEvent!!.id)
        }
    }

    if (showSOSConfirmDialog) {
        LaunchedEffect(showSOSConfirmDialog) {
            sosConfirmCountdown = 3
            while (sosConfirmCountdown > 0) {
                delay(1000)
                sosConfirmCountdown -= 1
            }
            showSOSConfirmDialog = false
            sosViewModel.triggerSOS(context, SOSTriggerMethod.BUTTON) { eventId ->
                onNavigateToAlert(eventId)
            }
        }

        AlertDialog(
            onDismissRequest = { showSOSConfirmDialog = false },
            containerColor = SafetyCardBackground,
            icon = {
                Icon(
                    Icons.Filled.CrisisAlert,
                    contentDescription = null,
                    tint = StatusDenied,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    "Trigger Emergency SOS?",
                    color = SafetyTextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Alerting in $sosConfirmCountdown seconds...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = StatusDenied
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Auto-call will be initiated and emergency location SMS will be dispatched immediately to your contacts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SafetyTextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                SafetyPillButton(
                    text = "TRIGGER NOW",
                    onClick = {
                        showSOSConfirmDialog = false
                        sosViewModel.triggerSOS(context, SOSTriggerMethod.BUTTON) { eventId ->
                            onNavigateToAlert(eventId)
                        }
                    },
                    containerColor = StatusDenied,
                    contentColor = Color.White
                )
            },
            dismissButton = {
                TextButton(onClick = { showSOSConfirmDialog = false }) {
                    Text("CANCEL", color = SafetyTextMuted)
                }
            }
        )
    }

    var showRequestsDialog by remember { mutableStateOf(false) }

    if (showRequestsDialog) {
        val dialogContext = LocalContext.current
        var actionInProgressId by remember { mutableStateOf<String?>(null) }
        val pendingList = state.pendingRequests

        AlertDialog(
            onDismissRequest = { showRequestsDialog = false },
            containerColor = SafetyCardBackground,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = SafetyPrimaryBlue,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Trusted Contact Requests",
                        color = SafetyTextPrimary,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (pendingList.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = StatusApproved,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No Pending Requests",
                                color = SafetyTextPrimary,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val displayUid = state.currentUid?.uid?.ifBlank {
                                state.userProfile?.currentWeeklyUid ?: state.userProfile?.authUserId ?: ""
                            } ?: ""
                            Text(
                                text = if (displayUid.isNotBlank())
                                    "When a trusted contact sends a request using your UID ($displayUid), it will appear here for you to accept or reject."
                                else
                                    "When a trusted contact sends a request using your UID, it will appear here for you to accept or reject.",
                                color = SafetyTextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Text(
                            text = "The following contacts entered your UID to monitor your safety and receive emergency SOS alerts:",
                            color = SafetyTextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        pendingList.forEach { rel ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = SafetyCardBackgroundElevated
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(SafetyPrimaryBlue.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Person,
                                                contentDescription = null,
                                                tint = SafetyPrimaryBlue,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = rel.trustedContactName.ifBlank { "Trusted Contact" },
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = SafetyTextPrimary
                                            )
                                            if (rel.trustedContactPhone.isNotBlank()) {
                                                Text(
                                                    text = "Phone: ${rel.trustedContactPhone}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = SafetyTextSecondary
                                                )
                                            }
                                            Text(
                                                text = "Requested: ${com.womensafety.system.utils.DateUtils.formatDateTime(rel.requestedAt)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SafetyTextMuted
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // ACCEPT BUTTON
                                        Button(
                                            onClick = {
                                                actionInProgressId = rel.id
                                                viewModel.approveRequest(rel.id) { success, errMsg ->
                                                    actionInProgressId = null
                                                    if (success) {
                                                        android.widget.Toast.makeText(
                                                            dialogContext,
                                                            "✓ Accepted! ${rel.trustedContactName.ifBlank { "Contact" }} is now connected.",
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                    } else {
                                                        android.widget.Toast.makeText(
                                                            dialogContext,
                                                            "Error: ${errMsg ?: "Could not accept"}",
                                                            android.widget.Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            enabled = actionInProgressId == null,
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = StatusApproved
                                            )
                                        ) {
                                            if (actionInProgressId == rel.id) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    color = Color.White,
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("ACCEPT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }

                                        // REJECT BUTTON
                                        OutlinedButton(
                                            onClick = {
                                                actionInProgressId = rel.id
                                                viewModel.denyRequest(rel.id) { success, errMsg ->
                                                    actionInProgressId = null
                                                    if (success) {
                                                        android.widget.Toast.makeText(
                                                            dialogContext,
                                                            "Request rejected.",
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                    } else {
                                                        android.widget.Toast.makeText(
                                                            dialogContext,
                                                            "Error: ${errMsg ?: "Could not reject"}",
                                                            android.widget.Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            enabled = actionInProgressId == null,
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = StatusDenied
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = StatusDenied
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("REJECT", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = StatusDenied)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRequestsDialog = false }) {
                    Text("Close", color = SafetyPrimaryBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    if (state.isLoading) {
        Box(
            Modifier
                .fillMaxSize()
                .background(SafetyDarkBackground),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = SafetyPrimaryBlue)
        }
        return
    }

    val activeSafetyModeStr = state.userProfile?.activeSafetyMode ?: "REGULAR"
    val activeMode = SafetyMode.fromString(activeSafetyModeStr)
    val modeSuggestion by SafetyMonitoringManager.modeSuggestion.collectAsState()
    val targetUid = state.currentUid?.uid?.ifBlank {
        state.userProfile?.currentWeeklyUid ?: state.userProfile?.authUserId ?: ""
    } ?: ""

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SafetyDarkBackground)
    ) {
        // Top App Bar matching Google Personal Safety
        SafetyTopAppBar(
            title = "Personal Safety",
            unreadNotificationsCount = state.pendingRequestCount,
            onNotificationClick = {
                viewModel.refreshRequests()
                showRequestsDialog = true
            },
            onProfileClick = { /* Handled via bottom bar or detail */ }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── Hero Illustration Card (Exact match to uploaded Image 2) ───
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = SafetyCardBackground
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Center Illustration
                    SafetyHeroIllustration(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    )

                    // 4 Floating Feature Badges
                    // Top-Left: Emergency Sharing / Location
                    SafetyHeroBadge(
                        icon = Icons.Filled.ShareLocation,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 24.dp, top = 28.dp),
                        onClick = {
                            if (targetUid.isNotBlank()) onNavigateToLiveMap(targetUid)
                            else onNavigateToLocationSettings()
                        }
                    )

                    // Top-Right: Emergency SOS
                    SafetyHeroBadge(
                        text = "SOS",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 24.dp, top = 28.dp),
                        onClick = {
                            sosConfirmCountdown = 3
                            showSOSConfirmDialog = true
                        }
                    )

                    // Bottom-Left: Travel Mode / Car detection
                    SafetyHeroBadge(
                        icon = Icons.Filled.DirectionsCar,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 24.dp, bottom = 32.dp),
                        onClick = onNavigateToTravelMode
                    )

                    // Bottom-Right: Power / Shake detection
                    SafetyHeroBadge(
                        icon = Icons.Filled.PowerSettingsNew,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 24.dp, bottom = 32.dp),
                        onClick = onNavigateToShakeSettings
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ─── Headline & Subtitle ─────────────────────────
            Text(
                text = "Do more with Personal Safety",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = SafetyTextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Emergency Sharing, Safety Check, Shake Detection, and more are actively protecting you",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                ),
                color = SafetyTextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Dual Action SOS & Safety Buttons (both SOS buttons trigger auto call + SMS) ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SafetyPillButton(
                    text = "Emergency SOS",
                    onClick = {
                        sosConfirmCountdown = 3
                        showSOSConfirmDialog = true
                    },
                    modifier = Modifier
                        .weight(1.1f)
                        .height(48.dp),
                    containerColor = StatusDenied,
                    contentColor = Color.White
                )
                OutlinedButton(
                    onClick = onNavigateToSafety,
                    modifier = Modifier
                        .weight(0.9f)
                        .height(48.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SafetyPrimaryBlue),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SafetyPrimaryBlue)
                ) {
                    Text("Safety Check", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ─── Safety Mode Suggestion Card (if active) ─────
            if (modeSuggestion != null && state.userProfile?.autoModeSuggestionsEnabled == true) {
                val suggestion = modeSuggestion!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SafetyCardBackground
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SafetyPrimaryBlue.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = SafetyPrimaryBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Safety Mode Suggestion",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SafetyTextPrimary
                                )
                                Text(
                                    "You appear to be ${if (suggestion == SafetyMode.TRAVELLING) "travelling" else "at saved location"}. Switch to ${suggestion.displayName}?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SafetyTextSecondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { SafetyMonitoringManager.dismissSuggestion() }) {
                                Text("Dismiss", color = SafetyTextMuted)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            SafetyPillButton(
                                text = "Switch Mode",
                                onClick = { viewModel.updateActiveSafetyMode(suggestion) },
                                modifier = Modifier.height(38.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ─── Operating Mode & System Status Group ────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = SafetyCardBackground
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E3A2F)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = StatusApproved,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "System Protected",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = SafetyTextPrimary
                            )
                            Text(
                                text = "Mode: ${activeMode.displayName} • Location sync active",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = SafetyTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = SafetyCardDivider)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "SAFETY OPERATING MODE",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        fontWeight = FontWeight.Medium,
                        color = SafetyTextMuted
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Mode Selector Pill Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SafetyMode.values().forEach { modeOption ->
                            val isSelected = activeMode == modeOption
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(
                                        if (isSelected) SafetyIndicatorBlue
                                        else Color(0xFF282B30)
                                    )
                                    .clickable { viewModel.updateActiveSafetyMode(modeOption) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = modeOption.displayName.split("/")[0],
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) SafetyPrimaryBlue else SafetyTextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Safety UID quick indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                            tint = SafetyPrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Safety UID: ${if (targetUid.isNotBlank()) targetUid else "Syncing..."}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SafetyTextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                if (targetUid.isNotBlank()) onNavigateToLiveMap(targetUid)
                            }
                        ) {
                            Text(
                                "Radar Map",
                                color = SafetyPrimaryBlue,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Mode-specific views for TRAVELLING, WORKING, and SLEEPING
            when (activeMode) {
                SafetyMode.TRAVELLING -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    TravellingModeView(
                        userProfile = state.userProfile,
                        onNavigateToTravel = onNavigateToTravelMode,
                        onNavigateToLiveMap = {
                            if (targetUid.isNotBlank()) onNavigateToLiveMap(targetUid)
                        }
                    )
                }
                SafetyMode.WORKING -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    WorkingModeView(
                        userProfile = state.userProfile,
                        viewModel = viewModel,
                        onNavigateToSafety = onNavigateToSafety
                    )
                }
                SafetyMode.SLEEPING -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    SleepingModeView(
                        userProfile = state.userProfile,
                        viewModel = viewModel,
                        onNavigateToSafety = onNavigateToSafety
                    )
                }
                else -> { /* Clean view */ }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Reusable Card component for backward compatibility across subviews
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
            .clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = SafetyCardBackground
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
