package com.womensafety.system.ui.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.data.model.JourneyRecord
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.DateUtils
import com.womensafety.system.viewmodel.TravelViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelHistoryScreen(
    viewModel: TravelViewModel,
    onBack: () -> Unit
) {
    val journeys by viewModel.pastJourneys.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Travel Journey Log",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark.copy(alpha = 0.9f))
            )
        },
        containerColor = BackgroundDark
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(BackgroundDark, Color(0xFF0F0B00))
                    )
                )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = GradientAmber
                )
            } else if (journeys.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = OnSurfaceVariantDark,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No travel logs found",
                        color = OnSurfaceVariantDark,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(journeys) { journey ->
                        JourneyHistoryItem(journey)
                    }
                }
            }
        }
    }
}

@Composable
fun JourneyHistoryItem(journey: JourneyRecord) {
    val durationMins = if (journey.endTime != null) {
        (journey.endTime - journey.startTime) / 1000 / 60
    } else {
        0L
    }

    val statusColor = when (journey.status.uppercase()) {
        "COMPLETED" -> StatusApproved
        "ESCALATED" -> StatusDenied
        else -> GradientGold
    }

    GlassCard(
        accentGradient = Brush.horizontalGradient(
            colors = listOf(statusColor.copy(alpha = 0.08f), statusColor.copy(alpha = 0.02f))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    DateUtils.formatDateTime(journey.startTime),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceDark
                )

                Text(
                    journey.status,
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = GlassBorder)
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                "Start Point: (${journey.startLatitude}, ${journey.startLongitude})",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariantDark
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Destination: (${journey.destLatitude}, ${journey.destLongitude})",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariantDark
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.DirectionsRun,
                    contentDescription = null,
                    tint = GradientAmber,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Duration: ${durationMins} mins • ${journey.routePoints.size} route coordinates logged",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceDark
                )
            }
        }
    }
}
