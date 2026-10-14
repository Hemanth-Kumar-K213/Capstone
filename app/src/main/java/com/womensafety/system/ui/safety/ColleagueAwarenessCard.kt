package com.womensafety.system.ui.safety

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.data.model.Colleague
import com.womensafety.system.data.model.ColleagueStatus
import com.womensafety.system.data.model.TransportSuggestion
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.ColleagueAwarenessViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColleagueAwarenessCard(
    viewModel: ColleagueAwarenessViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colleagues by viewModel.colleaguesAtWork.collectAsState()
    val safetyBuddy by viewModel.safetyBuddy.collectAsState()
    val incomingRequests by viewModel.incomingBuddyRequests.collectAsState()
    val isLateStay by viewModel.isLateStay.collectAsState()
    val transportOptions by viewModel.transportOptions.collectAsState()
    val bookingConfirmation by viewModel.bookingConfirmation.collectAsState()

    var showTransportSheet by remember { mutableStateOf(false) }

    // Transport Modal Dialog
    if (showTransportSheet) {
        AlertDialog(
            onDismissRequest = { showTransportSheet = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Verified Late-Stay Transport", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Select safe commute home. Your emergency contacts will receive an automatic tracking SMS with driver & companion details:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    transportOptions.forEach { option ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.bookTransport(option)
                                    showTransportSheet = false
                                }
                                .padding(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(option.transportType.iconEmoji, fontSize = 24.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(option.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(
                                    color = StatusApproved.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        "${option.etaMinutes}m",
                                        fontWeight = FontWeight.Bold,
                                        color = StatusApproved,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTransportSheet = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = modifier) {
        // ─── BOOKING CONFIRMATION BANNER ───────────────────────────────────
        if (bookingConfirmation != null) {
            AppCard(
                accentGradient = Brush.horizontalGradient(
                    colors = listOf(StatusApproved.copy(alpha = 0.2f), DeepCyan.copy(alpha = 0.1f))
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = StatusApproved)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        bookingConfirmation!!,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.clearConfirmation() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // ─── INCOMING BUDDY REQUESTS BANNER ──────────────────────────────
        if (incomingRequests.isNotEmpty()) {
            incomingRequests.forEach { request ->
                AppCard(
                    accentGradient = Brush.horizontalGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), DeepCyan.copy(alpha = 0.1f))
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Safety Buddy Request from ${request.fromName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "${request.fromName} wants to pair as your Workplace Safety Buddy for mutual departure check-ins.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { viewModel.acceptBuddyRequest(request.fromUserId) },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusApproved),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Text("Accept Buddy", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { viewModel.declineBuddyRequest(request.fromUserId) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Text("Decline", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // ─── LATE-STAY ALERT CARD ──────────────────────────────────────────
        AnimatedVisibility(visible = isLateStay) {
            AppCard(
                accentGradient = Brush.horizontalGradient(
                    colors = listOf(AmberOrange.copy(alpha = 0.25f), CoralRed.copy(alpha = 0.12f))
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AmberOrange.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Schedule, contentDescription = null, tint = AmberOrange)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "WORKING LATE PROTECTION ACTIVE",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                                fontWeight = FontWeight.Bold,
                                color = AmberOrange
                            )
                            Text(
                                "Past 6:30 PM Work Hours",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            color = AmberOrange,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Monitored",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Your emergency contacts have been notified that you are working late. Verified transport options are ready for safe commute home.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showTransportSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Icon(Icons.Filled.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Arrange Transport", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.simulateLateStay(false) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Text("Dismiss", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // ─── SAFETY BUDDY SECTION ──────────────────────────────────────────
        AppCard(
            accentGradient = Brush.horizontalGradient(
                colors = listOf(
                    StatusApproved.copy(alpha = 0.15f),
                    DeepCyan.copy(alpha = 0.05f)
                )
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(StatusApproved.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = StatusApproved, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "WORKPLACE SAFETY BUDDY",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                            fontWeight = FontWeight.Bold,
                            color = StatusApproved
                        )
                        Text(
                            text = safetyBuddy?.name ?: "No Partner Selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (safetyBuddy != null) {
                        Surface(
                            color = StatusApproved.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "Paired",
                                color = StatusApproved,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (safetyBuddy != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Status: ${safetyBuddy!!.currentStatus.displayName} • Mutual check-ins enabled for evening departures.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Pair with a trusted coworker below to share mutual departure notifications when working late.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ─── COLLEAGUES PRESENT AT WORKPLACE ───────────────────────────────
        AppCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Workspaces, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Colleagues at Workplace (${colleagues.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (colleagues.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BluetoothSearching,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No colleagues detected nearby",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "BLE beacon broadcast & workplace geofence monitoring active.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    colleagues.forEach { colleague ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    colleague.name.take(1),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(colleague.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    if (colleague.isSafetyBuddy) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Filled.Shield, contentDescription = "Safety Buddy", tint = StatusApproved, modifier = Modifier.size(14.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val (badgeLabel, badgeColor) = when (colleague.dataSource) {
                                        "BLE" -> "BLE • ${colleague.distanceMeters?.toInt() ?: 15}m" to StatusApproved
                                        "Geofence" -> "Geofence < 500m" to DeepCyan
                                        "Firestore" -> "Location History" to AmberOrange
                                        else -> colleague.dataSource to MaterialTheme.colorScheme.secondary
                                    }
                                    Surface(
                                        color = badgeColor.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            badgeLabel,
                                            color = badgeColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "${colleague.currentStatus.iconEmoji} ${colleague.currentStatus.displayName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Phone call button
                            if (!colleague.phone.isNullOrBlank()) {
                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${colleague.phone}"))
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Filled.Call, contentDescription = "Call", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }

                            // Send Buddy Request / Pair Button
                            if (!colleague.isSafetyBuddy) {
                                Button(
                                    onClick = { viewModel.sendBuddyRequest(colleague) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Pair", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        if (colleague != colleagues.last()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }

        // ─── CAPSTONE EVALUATOR DEMO SIMULATOR ─────────────────────────────
        // Essential test harness for Capstone live defense:
        // Allows demonstrating late-stay triggers past 6:30 PM & instant safety buddy pairing
        // without waiting for actual evening hours or requiring multi-device BLE testing.
        AppCard(
            accentGradient = Brush.horizontalGradient(
                colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), AmberOrange.copy(alpha = 0.08f))
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Working Mode Evaluator Simulator", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Instant demo controls for Capstone evaluation (triggers late-stay protection & simulates buddy pairing without peer devices):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.simulateLateStay(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberOrange),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text("Simulate Late Stay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val candidate = colleagues.firstOrNull { !it.isSafetyBuddy } ?: colleagues.firstOrNull()
                            if (candidate != null) {
                                viewModel.simulateBuddyPairing(candidate)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusApproved),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text("Set Buddy (Demo)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showTransportSheet = true },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Icon(Icons.Filled.DirectionsCar, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Show Transport Options Dialog", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
