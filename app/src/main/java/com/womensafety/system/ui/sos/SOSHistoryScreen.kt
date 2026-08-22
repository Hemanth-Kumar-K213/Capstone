package com.womensafety.system.ui.sos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.womensafety.system.data.model.SOSEvent
import com.womensafety.system.data.model.SOSStatus
import com.womensafety.system.ui.components.EmptyStateCard
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.DateUtils
import com.womensafety.system.viewmodel.SOSViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SOSHistoryScreen(
    viewModel: SOSViewModel,
    onNavigateToDetail: (String) -> Unit,
    onBack: () -> Unit
) {
    val history by viewModel.sosHistory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedFilter by remember { mutableStateOf("ALL") }
    val filterOptions = listOf("ALL", "ACTIVE", "RESOLVED", "FALSE_ALARM")

    LaunchedEffect(Unit) {
        viewModel.loadSOSHistory()
    }

    val filteredEvents = remember(history, selectedFilter) {
        when (selectedFilter) {
            "ACTIVE" -> history.filter { it.status == SOSStatus.ACTIVE }
            "RESOLVED" -> history.filter { it.status == SOSStatus.RESOLVED || it.status == SOSStatus.AUTO_RESOLVED }
            "FALSE_ALARM" -> history.filter { it.status == SOSStatus.FALSE_ALARM }
            else -> history
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "SOS Incident History",
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
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filterOptions) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                when (filter) {
                                    "ALL" -> "All (${history.size})"
                                    "ACTIVE" -> "Active"
                                    "RESOLVED" -> "Resolved"
                                    "FALSE_ALARM" -> "False Alarms"
                                    else -> filter
                                }
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GradientAmber.copy(alpha = 0.2f),
                            selectedLabelColor = GradientAmber
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = GradientAmber)
                }
            } else if (filteredEvents.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Filled.Shield,
                    title = "No Incidents Found",
                    description = "No SOS records matching the selected filter criteria."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredEvents, key = { it.id }) { event ->
                        SOSHistoryCard(
                            event = event,
                            onClick = { onNavigateToDetail(event.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SOSHistoryCard(
    event: SOSEvent,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.clickable { onClick() },
        accentGradient = if (event.status == SOSStatus.ACTIVE) {
            Brush.horizontalGradient(
                colors = listOf(StatusDenied.copy(alpha = 0.18f), StatusDenied.copy(alpha = 0.05f))
            )
        } else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            when (event.status) {
                                SOSStatus.ACTIVE -> StatusDenied.copy(alpha = 0.15f)
                                SOSStatus.RESOLVED -> StatusApproved.copy(alpha = 0.15f)
                                else -> StatusPending.copy(alpha = 0.15f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when (event.status) {
                            SOSStatus.ACTIVE -> Icons.Filled.Warning
                            SOSStatus.RESOLVED -> Icons.Filled.CheckCircle
                            else -> Icons.Filled.Info
                        },
                        contentDescription = null,
                        tint = when (event.status) {
                            SOSStatus.ACTIVE -> StatusDenied
                            SOSStatus.RESOLVED -> StatusApproved
                            else -> StatusPending
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        DateUtils.formatDateTime(event.triggeredAt),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceDark
                    )
                    Text(
                        "Trigger Method: ${event.triggerMethod.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantDark
                    )
                }

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
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (!event.address.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    event.address!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariantDark
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SMS Dispatched: ${event.smsSent} • Call: ${if (event.callMade) "Yes" else "No"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = GradientAmber
                )

                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = OnSurfaceVariantDark.copy(alpha = 0.6f)
                )
            }
        }
    }
}
