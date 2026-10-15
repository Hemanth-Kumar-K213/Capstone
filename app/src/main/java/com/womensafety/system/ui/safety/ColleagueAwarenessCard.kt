package com.womensafety.system.ui.safety

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import com.womensafety.system.data.model.BuddyRequest
import com.womensafety.system.data.model.Colleague
import com.womensafety.system.data.model.ColleagueStatus
import com.womensafety.system.data.model.TransportSuggestion
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.ColleagueAwarenessViewModel

/**
 * Requirement 2F: Real Colleague Awareness Card.
 * Display ONLY real discovered colleagues and real mutual buddy requests.
 *
 * Tab 1: Colleagues At Workplace (BLE / Geofence / Location History badges)
 * Tab 2: Incoming Buddy Requests (Accept / Decline)
 * Tab 3: Active Safety Buddies (Paired status, Direct Call, Share ETA)
 *
 * ZERO hardcoded names. ZERO simulator buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColleagueAwarenessCard(
    viewModel: ColleagueAwarenessViewModel,
    onNavigateToBleDiagnostic: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colleagues by viewModel.colleaguesAtWork.collectAsState()
    val safetyBuddy by viewModel.safetyBuddy.collectAsState()
    val incomingRequests by viewModel.incomingBuddyRequests.collectAsState()
    val isLateStay by viewModel.isLateStay.collectAsState()
    val transportOptions by viewModel.transportOptions.collectAsState()
    val bookingConfirmation by viewModel.bookingConfirmation.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0: Colleagues, 1: Incoming Requests, 2: Active Buddies
    var showTransportSheet by remember { mutableStateOf(false) }

    // Active paired buddies: safetyBuddy or colleagues marked isSafetyBuddy
    val activeBuddies = remember(colleagues, safetyBuddy) {
        val list = mutableListOf<Colleague>()
        safetyBuddy?.let { list.add(it) }
        colleagues.filter { it.isSafetyBuddy && it.id != safetyBuddy?.id }.forEach { list.add(it) }
        list.distinctBy { it.id }
    }

    // Transport Modal Dialog for Verified Evening Commutes
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
                        "Select verified safe commute. Emergency contacts receive an automated SMS with ETA & driver tracking:",
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
        // Confirmation Banner
        if (bookingConfirmation != null) {
            AppCard(
                accentGradient = Brush.horizontalGradient(
                    colors = listOf(StatusApproved.copy(alpha = 0.2f), DeepCyan.copy(alpha = 0.1f))
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
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

        // Late Stay Notice
        if (isLateStay) {
            AppCard(
                accentGradient = Brush.horizontalGradient(
                    colors = listOf(AmberOrange.copy(alpha = 0.22f), CoralRed.copy(alpha = 0.1f))
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.NightlightRound, contentDescription = null, tint = AmberOrange)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Late Stay Detected", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text("Working late past scheduled hours. Arrange verified transport home:", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = { showTransportSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberOrange),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Book Cab", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Main Colleague Awareness Card with Tabs
        AppCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Diversity3, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Colleague Presence & Safety Buddy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("BLE • 500m Geofence • Mutual Protection", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onNavigateToBleDiagnostic) {
                        Icon(
                            imageVector = Icons.Default.BluetoothSearching,
                            contentDescription = "BLE Diagnostic Tool",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // 3 Tabs
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = {
                            Text(
                                "Colleagues (${colleagues.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Requests", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                if (incomingRequests.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        color = EmergencyRed,
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            "${incomingRequests.size}",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                    Tab(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        text = {
                            Text(
                                "Active Buddies (${activeBuddies.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }

                // Tab Content
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (activeTab) {
                        0 -> {
                            // ─── TAB 1: COLLEAGUES AT WORKPLACE ────────────────
                            if (colleagues.isNotEmpty()) {
                                colleagues.forEach { colleague ->
                                    ColleagueItemRow(
                                        colleague = colleague,
                                        onPair = {
                                            viewModel.sendBuddyRequest(colleague)
                                            Toast.makeText(context, "Pairing request sent to ${colleague.name}", Toast.LENGTH_SHORT).show()
                                        },
                                        onCall = {
                                            val phone = colleague.phone ?: ""
                                            val dialIntent = if (phone.isNotBlank()) {
                                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                            } else {
                                                Intent(Intent.ACTION_DIAL)
                                            }
                                            context.startActivity(dialIntent)
                                        }
                                    )
                                }
                            } else {
                                EmptyColleagueView(
                                    title = "No Colleagues Nearby",
                                    subtitle = "Colleagues running Trinetra with Bluetooth enabled will appear here automatically via BLE & Geofence detection."
                                )
                            }
                        }

                        1 -> {
                            // ─── TAB 2: INCOMING BUDDY REQUESTS ────────────────
                            if (incomingRequests.isNotEmpty()) {
                                incomingRequests.forEach { request ->
                                    IncomingRequestCard(
                                        request = request,
                                        onAccept = {
                                            viewModel.acceptBuddyRequest(request)
                                            Toast.makeText(context, "Accepted Safety Buddy request from ${request.fromName}", Toast.LENGTH_SHORT).show()
                                        },
                                        onDecline = {
                                            viewModel.declineBuddyRequest(request)
                                            Toast.makeText(context, "Declined request", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            } else {
                                EmptyColleagueView(
                                    title = "No Pending Requests",
                                    subtitle = "When a colleague sends a mutual Safety Buddy invitation, it will appear here for you to accept."
                                )
                            }
                        }

                        2 -> {
                            // ─── TAB 3: ACTIVE SAFETY BUDDIES ──────────────────
                            if (activeBuddies.isNotEmpty()) {
                                activeBuddies.forEach { buddy ->
                                    ActiveBuddyCard(
                                        buddy = buddy,
                                        onCall = {
                                            val phone = buddy.phone ?: ""
                                            val dialIntent = if (phone.isNotBlank()) {
                                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                            } else {
                                                Intent(Intent.ACTION_DIAL)
                                            }
                                            context.startActivity(dialIntent)
                                        },
                                        onShareEta = {
                                            val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_TEXT, "Hi ${buddy.name}, I'm leaving the workplace now. My estimated travel ETA home is 25 minutes. - Sent via Trinetra Safety Buddy")
                                                type = "text/plain"
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Share ETA with ${buddy.name}"))
                                        }
                                    )
                                }
                            } else {
                                EmptyColleagueView(
                                    title = "No Active Safety Buddies",
                                    subtitle = "Mutual pairing connects you with a designated peer. Tap 'PAIR' on any discovered colleague in Tab 1 to initiate mutual protection."
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColleagueItemRow(
    colleague: Colleague,
    onPair: () -> Unit,
    onCall: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = colleague.name.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(colleague.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.width(6.dp))
                    if (colleague.isSafetyBuddy) {
                        Surface(
                            color = StatusApproved.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "Paired ✓",
                                color = StatusApproved,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Badge: BLE • 15m OR Geofence • 450m OR Location History (<5m)
                val detectionBadge = when {
                    colleague.detectionMethod == "BLE" -> "BLE • ${colleague.distanceMeters?.toInt() ?: 15}m"
                    colleague.detectionMethod == "Geofence" -> "Geofence • ${colleague.distanceMeters?.toInt() ?: 450}m"
                    else -> colleague.detectionMethod
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = detectionBadge,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "${colleague.currentStatus.displayName} • ${formatLastSeen(colleague.lastSeen)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Actions: Call button & Pair button
            IconButton(onClick = onCall, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Phone, contentDescription = "Call", tint = StatusApproved, modifier = Modifier.size(20.dp))
            }

            if (!colleague.isSafetyBuddy) {
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = onPair,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PAIR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun IncomingRequestCard(
    request: BuddyRequest,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${request.fromName} wants to be your Safety Buddy",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Received ${formatLastSeen(request.receivedAt)} • Mutual check-in monitoring",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDecline,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Text("Decline", fontSize = 12.sp)
                }
                Button(
                    onClick = onAccept,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusApproved),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Accept", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ActiveBuddyCard(
    buddy: Colleague,
    onCall: () -> Unit,
    onShareEta: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = StatusApproved.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, StatusApproved.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(StatusApproved.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = StatusApproved, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(buddy.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = StatusApproved,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "ACTIVE BUDDY",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        "${buddy.currentStatus.displayName} • ${formatLastSeen(buddy.lastSeen)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onCall,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusApproved),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Call Buddy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onShareEta,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Icon(Icons.Filled.ShareLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share ETA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EmptyColleagueView(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.BluetoothSearching,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(38.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun formatLastSeen(time: Long): String {
    val diff = System.currentTimeMillis() - time
    val mins = diff / (60 * 1000)
    return when {
        mins < 1 -> "Just now"
        mins < 60 -> "${mins}m ago"
        else -> "${mins / 60}h ago"
    }
}
