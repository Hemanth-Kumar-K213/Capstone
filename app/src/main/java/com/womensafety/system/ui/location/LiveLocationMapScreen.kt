package com.womensafety.system.ui.location

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveLocationMapScreen(
    userId: String,
    viewModel: LocationViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val liveLocation by viewModel.observeUserLocation(userId).collectAsState(initial = null)

    val defaultLatLng = LatLng(20.5937, 78.9629) // India center default
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
                        liveLocation?.userName?.ifBlank { "Live Location" } ?: "Live Location",
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
        ) {
            // Google Map View
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = true,
                    compassEnabled = true
                ),
                properties = MapProperties(
                    isMyLocationEnabled = false
                )
            ) {
                if (liveLocation != null && liveLocation!!.latitude != 0.0) {
                    Marker(
                        state = MarkerState(position = userLatLng),
                        title = liveLocation!!.userName.ifBlank { "User Location" },
                        snippet = "Updated: ${DateUtils.formatDateTime(liveLocation!!.timestamp)}"
                    )

                    Circle(
                        center = userLatLng,
                        radius = (liveLocation!!.accuracy.coerceAtLeast(15f)).toDouble(),
                        fillColor = GradientAmber.copy(alpha = 0.2f),
                        strokeColor = GradientAmber,
                        strokeWidth = 2f
                    )
                }
            }

            // User Info Floating Overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                GlassCard(
                    accentGradient = Brush.horizontalGradient(
                        colors = listOf(GradientAmber.copy(alpha = 0.15f), GradientRose.copy(alpha = 0.1f))
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(GradientAmber.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.PersonPinCircle, contentDescription = null, tint = GradientAmber)
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    liveLocation?.userName?.ifBlank { "Connected User" } ?: "Connected User",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceDark
                                )

                                if (liveLocation != null) {
                                    Text(
                                        "Updated ${DateUtils.formatDateTime(liveLocation!!.timestamp)} (±${liveLocation!!.accuracy.toInt()}m)",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = GradientGold
                                    )
                                } else {
                                    Text(
                                        "Awaiting location update...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceVariantDark
                                    )
                                }
                            }
                        }

                        if (!liveLocation?.address.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                liveLocation!!.address!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceDark
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
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
                                    .weight(1f)
                                    .height(48.dp),
                                enabled = liveLocation != null,
                                colors = ButtonDefaults.buttonColors(containerColor = GradientAmber),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("NAVIGATE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            OutlinedButton(
                                onClick = {
                                    if (liveLocation != null) {
                                        val shareText = "Live location of ${liveLocation!!.userName}: https://maps.google.com/?q=${liveLocation!!.latitude},${liveLocation!!.longitude}"
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Location"))
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                enabled = liveLocation != null,
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceDark)
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SHARE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
