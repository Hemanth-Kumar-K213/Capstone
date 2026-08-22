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
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.womensafety.system.data.model.UserLocation
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.DateUtils
import com.womensafety.system.viewmodel.LocationViewModel
import com.womensafety.system.viewmodel.SOSViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SOSReceivedScreen(
    eventId: String,
    userId: String,
    locationViewModel: LocationViewModel,
    sosViewModel: SOSViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val liveLocation by locationViewModel.observeUserLocation(userId).collectAsState(initial = null)
    val activeSOSEvent by sosViewModel.observeActiveSOS(userId).collectAsState(initial = null)

    val defaultLatLng = LatLng(20.5937, 78.9629)
    val userLatLng = remember(liveLocation) {
        if (liveLocation != null && liveLocation!!.latitude != 0.0) {
            LatLng(liveLocation!!.latitude, liveLocation!!.longitude)
        } else {
            defaultLatLng
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(userLatLng, 15f)
    }

    LaunchedEffect(userLatLng) {
        if (liveLocation != null && liveLocation!!.latitude != 0.0) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(userLatLng, 16f)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Emergency Alert Received",
                        color = StatusDenied,
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
                        colors = listOf(
                            StatusDenied.copy(alpha = 0.2f),
                            BackgroundDark
                        )
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Urgent Alert Card
            GlassCard(
                accentGradient = Brush.horizontalGradient(
                    colors = listOf(StatusDenied.copy(alpha = 0.3f), StatusDenied.copy(alpha = 0.1f))
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(StatusDenied.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = StatusDenied, modifier = Modifier.size(26.dp))
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🚨 ${liveLocation?.userName?.ifBlank { "Connected User" } ?: "Connected User"} NEEDS HELP!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = StatusDenied
                            )

                            if (activeSOSEvent != null) {
                                Text(
                                    "Triggered: ${DateUtils.formatDateTime(activeSOSEvent!!.triggeredAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceDark
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Map Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(MaterialTheme.shapes.large)
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(zoomControlsEnabled = false, compassEnabled = true)
                ) {
                    if (liveLocation != null && liveLocation!!.latitude != 0.0) {
                        Marker(
                            state = MarkerState(position = userLatLng),
                            title = liveLocation!!.userName.ifBlank { "User" },
                            snippet = "Active SOS"
                        )
                        Circle(
                            center = userLatLng,
                            radius = (liveLocation!!.accuracy.coerceAtLeast(20f)).toDouble(),
                            fillColor = StatusDenied.copy(alpha = 0.25f),
                            strokeColor = StatusDenied,
                            strokeWidth = 2f
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Address & Accuracy Info
            if (liveLocation != null) {
                GlassCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "CURRENT LOCATION",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                            color = GradientGold,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        if (!liveLocation!!.address.isNullOrBlank()) {
                            Text(
                                liveLocation!!.address!!,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurfaceDark
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        Text(
                            "Lat: ${String.format("%.5f", liveLocation!!.latitude)}, Lng: ${String.format("%.5f", liveLocation!!.longitude)} (±${liveLocation!!.accuracy.toInt()}m)",
                            style = MaterialTheme.typography.bodySmall,
                            color = GradientAmber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Button(
                onClick = {
                    if (liveLocation != null) {
                        val gmmIntentUri = Uri.parse("google.navigation:q=${liveLocation!!.latitude},${liveLocation!!.longitude}")
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                            setPackage("com.google.android.apps.maps")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        if (mapIntent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(mapIntent)
                        } else {
                            val webUri = Uri.parse("https://maps.google.com/?q=${liveLocation!!.latitude},${liveLocation!!.longitude}")
                            context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = liveLocation != null,
                colors = ButtonDefaults.buttonColors(containerColor = GradientAmber),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Filled.Navigation, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("START TURN-BY-TURN NAVIGATION", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceDark)
            ) {
                Text("DISMISS ALERT", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
