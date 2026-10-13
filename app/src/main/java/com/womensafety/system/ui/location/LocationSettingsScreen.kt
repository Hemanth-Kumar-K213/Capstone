package com.womensafety.system.ui.location

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.data.model.LocationServiceState
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.components.PrimaryButton
import com.womensafety.system.ui.components.SecondaryButton
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.DateUtils
import com.womensafety.system.viewmodel.LocationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSettingsScreen(
    viewModel: LocationViewModel,
    onNavigateToHistory: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToMapTest: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val serviceState by viewModel.serviceState.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val intervalMs by viewModel.updateIntervalMs.collectAsState()

    var sliderValue by remember(intervalMs) { mutableFloatStateOf((intervalMs / 1000).toFloat()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Location Settings",
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
            Text(
                text = "GPS TRACKING SERVICE",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Manage background GPS streaming to share your coordinates with approved trusted contacts in real-time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Service Status Card
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isTracking = serviceState == LocationServiceState.TRACKING
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isTracking) StatusApproved.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = if (isTracking) StatusApproved else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Service Status",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            when (serviceState) {
                                LocationServiceState.TRACKING -> "Active (Streaming Coordinates)"
                                LocationServiceState.PAUSED -> "Paused"
                                LocationServiceState.STARTING -> "Starting..."
                                LocationServiceState.STOPPING -> "Stopping..."
                                else -> "Inactive"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isTracking) StatusApproved else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (currentLocation != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Latest Coordinate Fix",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Lat: ${String.format("%.5f", currentLocation!!.latitude)}, Lng: ${String.format("%.5f", currentLocation!!.longitude)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (!currentLocation!!.address.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            currentLocation!!.address!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Updated: ${DateUtils.formatDateTime(currentLocation!!.timestamp)} (±${currentLocation!!.accuracy.toInt()}m accuracy)",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Update Interval Customization
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "UPDATE INTERVAL",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Current: ${sliderValue.toInt()} seconds (boosts to 5s automatically during SOS)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        viewModel.updateInterval(context, (sliderValue.toInt() * 1000).toLong())
                    },
                    valueRange = 5f..60f,
                    steps = 10,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "5s (High Precision)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "60s (Battery Saver)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Service Control Buttons
            if (serviceState == LocationServiceState.TRACKING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.pauseLocationTracking(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PAUSE", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.stopLocationTracking(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp), tint = OnPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("STOP", fontWeight = FontWeight.Bold, color = OnPrimary)
                    }
                }
            } else if (serviceState == LocationServiceState.PAUSED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.resumeLocationTracking(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp), tint = OnPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("RESUME", fontWeight = FontWeight.Bold, color = OnPrimary)
                    }

                    Button(
                        onClick = { viewModel.stopLocationTracking(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp), tint = OnPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("STOP", fontWeight = FontWeight.Bold, color = OnPrimary)
                    }
                }
            } else {
                PrimaryButton(
                    text = "Start Location Tracking",
                    onClick = { viewModel.startLocationTracking(context) },
                    icon = Icons.Filled.PlayArrow
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // History Button
            SecondaryButton(
                text = "View Location History",
                onClick = onNavigateToHistory,
                icon = Icons.Filled.History
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Diagnostics Button
            SecondaryButton(
                text = "Run GPS Diagnostics",
                onClick = onNavigateToDiagnostics,
                icon = Icons.Filled.GpsFixed
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Map Test Button
            SecondaryButton(
                text = "Run Map Test",
                onClick = onNavigateToMapTest,
                icon = Icons.Filled.Map
            )

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
