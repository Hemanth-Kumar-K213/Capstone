package com.womensafety.system.ui.sos

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.data.model.SOSStatus
import com.womensafety.system.ui.home.GlassCard
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
                        color = OnSurfaceDark,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = OnSurfaceDark
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
                            Icon(Icons.Filled.Share, contentDescription = "Export", tint = GradientGold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(GradientDeepPurple.copy(alpha = 0.5f), BackgroundDark)
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            if (event == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Incident not found.", color = OnSurfaceVariantDark)
                }
                return@Column
            }

            // Incident Header Card
            GlassCard(
                accentGradient = if (event.status == SOSStatus.ACTIVE) {
                    Brush.horizontalGradient(
                        colors = listOf(StatusDenied.copy(alpha = 0.2f), StatusDenied.copy(alpha = 0.05f))
                    )
                } else null
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "STATUS",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                            color = GradientGold,
                            fontWeight = FontWeight.Bold
                        )

                        Surface(
                            color = when (event.status) {
                                SOSStatus.ACTIVE -> StatusDenied.copy(alpha = 0.2f)
                                SOSStatus.RESOLVED -> StatusApproved.copy(alpha = 0.2f)
                                else -> StatusPending.copy(alpha = 0.2f)
                            },
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                event.status.name,
                                color = when (event.status) {
                                    SOSStatus.ACTIVE -> StatusDenied
                                    SOSStatus.RESOLVED -> StatusApproved
                                    else -> StatusPending
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        "Triggered on ${DateUtils.formatDateTime(event.triggeredAt)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceDark
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        "Trigger Mechanism: ${event.triggerMethod.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariantDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Timeline Card
            GlassCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "INCIDENT TIMELINE",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                        color = GradientGold,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TimelineRow(
                        title = "SOS Triggered",
                        subtitle = "${DateUtils.formatDateTime(event.triggeredAt)} via ${event.triggerMethod.name}",
                        icon = Icons.Filled.Warning,
                        color = StatusDenied
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = GlassBorder)

                    TimelineRow(
                        title = "Emergency SMS Dispatched",
                        subtitle = "${event.smsSent} cellular SMS messages dispatched",
                        icon = Icons.Filled.Sms,
                        color = GradientAmber
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = GlassBorder)

                    TimelineRow(
                        title = "Auto-Call Attempt",
                        subtitle = if (event.callMade) "Initiated phone call to primary emergency contact" else "Auto-call not configured or disabled",
                        icon = Icons.Filled.PhoneInTalk,
                        color = if (event.callMade) StatusApproved else OnSurfaceVariantDark
                    )

                    if (event.resolvedAt != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = GlassBorder)

                        TimelineRow(
                            title = "Resolved / Closed",
                            subtitle = "${DateUtils.formatDateTime(event.resolvedAt)} (${event.status.name})",
                            icon = Icons.Filled.CheckCircle,
                            color = StatusApproved
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Location Snapshot Card
            if (event.latitude != null && event.longitude != null) {
                GlassCard {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "INCIDENT COORDINATES",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                            color = GradientGold,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            "Lat: ${event.latitude}, Lng: ${event.longitude}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = OnSurfaceDark
                        )

                        if (!event.address.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                event.address!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariantDark
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                val mapIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("geo:${event.latitude},${event.longitude}?q=${event.latitude},${event.longitude}(SOS+Incident)")
                                ).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(mapIntent)
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GradientAmber),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("OPEN ON GOOGLE MAPS", fontWeight = FontWeight.Bold)
                        }
                    }
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = OnSurfaceDark)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariantDark)
        }
    }
}
