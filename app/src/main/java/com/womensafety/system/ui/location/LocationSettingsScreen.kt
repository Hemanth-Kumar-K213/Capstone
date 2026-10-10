package com.womensafety.system.ui.location

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
import com.womensafety.system.data.model.LocationServiceState
import com.womensafety.system.ui.home.GlassCard
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                text = "GPS TRACKING SERVICE",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                color = GradientGold,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Manage background GPS streaming to share your coordinates with approved trusted contacts.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariantDark
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Service Status Card
            GlassCard(
                accentGradient = Brush.horizontalGradient(
                    colors = listOf(
                        GradientAmber.copy(alpha = 0.08f),
                        GradientGold.copy(alpha = 0.04f)
                    )
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (serviceState == LocationServiceState.TRACKING) StatusApproved.copy(alpha = 0.15f)
                                    else StatusDenied.copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = if (serviceState == LocationServiceState.TRACKING) StatusApproved else StatusDenied,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Service Status",
                                style = MaterialTheme.typography.labelMedium,
                                color = OnSurfaceVariantDark
                            )
                            Text(
                                when (serviceState) {
                                    LocationServiceState.TRACKING -> "Active (Tracking)"
                                    LocationServiceState.PAUSED -> "Paused"
                                    LocationServiceState.STARTING -> "Initializing..."
                                    LocationServiceState.STOPPING -> "Stopping..."
                                    else -> "Inactive"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (serviceState == LocationServiceState.TRACKING) StatusApproved else OnSurfaceDark
                            )
                        }
                    }

                    if (currentLocation != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = GlassBorder)
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            "Latest Coordinate Fix",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariantDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Lat: ${String.format("%.5f", currentLocation!!.latitude)}, Lng: ${String.format("%.5f", currentLocation!!.longitude)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = OnSurfaceDark
                        )

                        if (!currentLocation!!.address.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                currentLocation!!.address!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariantDark
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Updated: ${DateUtils.formatDateTime(currentLocation!!.timestamp)} (±${currentLocation!!.accuracy.toInt()}m)",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = GradientAmber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Update Interval Customization
            GlassCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Update Interval",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Current: ${sliderValue.toInt()} seconds (boosts to 5s during SOS)",
                        style = MaterialTheme.typography.bodySmall,
                        color = GradientAmber
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = {
                            viewModel.updateInterval(context, (sliderValue.toInt() * 1000).toLong())
                        },
                        valueRange = 5f..60f,
                        steps = 10,
                        colors = SliderDefaults.colors(
                            thumbColor = GradientAmber,
                            activeTrackColor = GradientAmber,
                            inactiveTrackColor = OutlineDark
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("5 sec (High Battery)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = OnSurfaceVariantDark)
                        Text("60 sec (Battery Saver)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = OnSurfaceVariantDark)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Service Control Buttons
            if (serviceState == LocationServiceState.TRACKING) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.pauseLocationTracking(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GradientGold),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PAUSE", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = { viewModel.stopLocationTracking(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDenied),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("STOP", fontWeight = FontWeight.Bold)
                    }
                }
            } else if (serviceState == LocationServiceState.PAUSED) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.resumeLocationTracking(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GradientAmber),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("RESUME", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = { viewModel.stopLocationTracking(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDenied),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("STOP", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = { viewModel.startLocationTracking(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GradientAmber),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("START LOCATION TRACKING", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // History Button
            OutlinedButton(
                onClick = onNavigateToHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceDark)
            ) {
                Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("VIEW LOCATION HISTORY", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Diagnostics Button
            OutlinedButton(
                onClick = onNavigateToDiagnostics,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GradientAmber),
                border = androidx.compose.foundation.BorderStroke(1.dp, GradientAmber)
            ) {
                Icon(Icons.Filled.GpsFixed, contentDescription = null, modifier = Modifier.size(20.dp), tint = GradientAmber)
                Spacer(modifier = Modifier.width(8.dp))
                Text("RUN GPS DIAGNOSTICS", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Map Test Button
            OutlinedButton(
                onClick = onNavigateToMapTest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GradientAmber),
                border = androidx.compose.foundation.BorderStroke(1.dp, GradientAmber)
            ) {
                Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(20.dp), tint = GradientAmber)
                Spacer(modifier = Modifier.width(8.dp))
                Text("RUN MAP TEST", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
