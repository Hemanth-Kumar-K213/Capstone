package com.womensafety.system.ui.sos

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.data.model.SOSStatus
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.components.PrimaryButton
import com.womensafety.system.ui.components.SecondaryButton
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.DateUtils
import com.womensafety.system.viewmodel.SOSViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SOSHistoryDetailScreen(
    eventId: String,
    viewModel: SOSViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val history by viewModel.sosHistory.collectAsState()
    val event = remember(history, eventId) { history.find { it.id == eventId } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Incident Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (event != null) {
                        IconButton(onClick = {
                            val shareText = "Women Safety SOS Incident Report\n" +
                                    "Event ID: ${event.id}\n" +
                                    "Triggered: ${DateUtils.formatDateTime(event.triggeredAt)}\n" +
                                    "Status: ${event.status.name}\n" +
                                    "Method: ${event.triggerMethod.name}\n" +
                                    "Location: ${event.latitude}, ${event.longitude}\n" +
                                    "SMS Sent: ${event.smsSent}\n" +
                                    "Resolved: ${if (event.resolvedAt != null) DateUtils.formatDateTime(event.resolvedAt) else "N/A"}"
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Export Incident Report"))
                        }) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = "Export",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            if (event == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Incident not found.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                return@Column
            }

            val statusColor = when (event.status) {
                SOSStatus.ACTIVE -> EmergencyRed
                SOSStatus.RESOLVED -> StatusApproved
                else -> StatusPending
            }

            // Incident Header Card
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "INCIDENT OVERVIEW",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        color = statusColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            event.status.name,
                            color = statusColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    "Triggered on ${DateUtils.formatDateTime(event.triggeredAt)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    "Trigger Mechanism: ${event.triggerMethod.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Timeline Card
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "INCIDENT TIMELINE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                TimelineRow(
                    title = "SOS Triggered",
                    subtitle = "${DateUtils.formatDateTime(event.triggeredAt)} via ${event.triggerMethod.name}",
                    icon = Icons.Filled.Warning,
                    color = EmergencyRed
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )

                TimelineRow(
                    title = "Emergency SMS Dispatched",
                    subtitle = "${event.smsSent} cellular SMS messages dispatched",
                    icon = Icons.Filled.Sms,
                    color = MaterialTheme.colorScheme.primary
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )

                TimelineRow(
                    title = "Auto-Call Attempt",
                    subtitle = if (event.callMade) "Initiated phone call to primary emergency contact" else "Auto-call not configured or disabled",
                    icon = Icons.Filled.PhoneInTalk,
                    color = if (event.callMade) StatusApproved else MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (event.resolvedAt != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )

                    TimelineRow(
                        title = "Resolved / Closed",
                        subtitle = "${DateUtils.formatDateTime(event.resolvedAt)} (${event.status.name})",
                        icon = Icons.Filled.CheckCircle,
                        color = StatusApproved
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Location Snapshot Card
            if (event.latitude != null && event.longitude != null) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "INCIDENT COORDINATES",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Lat: ${event.latitude}, Lng: ${event.longitude}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (!event.address.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            event.address!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    PrimaryButton(
                        text = "Open on Google Maps",
                        onClick = {
                            val mapIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("geo:${event.latitude},${event.longitude}?q=${event.latitude},${event.longitude}(SOS+Incident)")
                            ).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(mapIntent)
                        },
                        icon = Icons.Filled.Map
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TimelineRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: androidx.compose.ui.graphics.Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
