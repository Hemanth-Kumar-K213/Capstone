package com.womensafety.system.ui.location

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.womensafety.system.data.model.UserLocation
import com.womensafety.system.manager.TrinetraLocationManager
import com.womensafety.system.service.LocationTrackingService
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.components.PrimaryButton
import com.womensafety.system.ui.components.SecondaryButton
import com.womensafety.system.ui.map.SafetyMapTracker
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.DateUtils
import com.womensafety.system.utils.MapUtils
import com.womensafety.system.viewmodel.LocationViewModel
import kotlinx.coroutines.launch

/**
 * Live Location Map Screen using OpenStreetMap (OSMDroid).
 * 100% Free & Open-Source (No Google Maps API keys or billing needed).
 * Renders the live map immediately, handles runtime permissions and GPS enablement,
 * and streams coordinates every 10 seconds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveLocationMapScreen(
    userId: String,
    viewModel: LocationViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val liveLocation by viewModel.observeLocationByUserIdOrUid(userId).collectAsState(initial = null)
    val localServiceLocation by LocationTrackingService.currentLocation.collectAsState()
    var immediateFix by remember { mutableStateOf<UserLocation?>(null) }
    var isGpsOff by remember { mutableStateOf(!TrinetraLocationManager.isGpsEnabled(context)) }

    // Launcher for runtime location permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            LocationTrackingService.startTracking(context)
            coroutineScope.launch {
                val fix = TrinetraLocationManager.getImmediateLocationFix(context, userId)
                if (fix != null) immediateFix = fix
            }
        }
    }

    // Check permissions and acquire instant location fix on mount
    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            if (!LocationTrackingService.isRunning) {
                LocationTrackingService.startTracking(context)
            }
            val fix = TrinetraLocationManager.getImmediateLocationFix(context, userId)
            if (fix != null) {
                immediateFix = fix
            }
        }
    }

    // Refresh GPS status periodically
    LaunchedEffect(Unit) {
        isGpsOff = !TrinetraLocationManager.isGpsEnabled(context)
    }

    // Determine the most accurate available coordinate fix
    val effectiveLocation = liveLocation ?: localServiceLocation ?: immediateFix
    val hasFix = effectiveLocation != null && (effectiveLocation.latitude != 0.0 || effectiveLocation.longitude != 0.0)

    val lat = if (hasFix) effectiveLocation!!.latitude else 12.9716
    val lng = if (hasFix) effectiveLocation!!.longitude else 77.5946

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = effectiveLocation?.userName?.ifBlank { "Live Radar Map" } ?: "Live Radar Map",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = if (hasFix) "10s GPS Sync Active" else "Acquiring satellite fix...",
                            color = if (hasFix) StatusApproved else AmberWarning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // OpenStreetMap (OSMDroid) Live Map Tracker
            SafetyMapTracker(
                userLatitude = lat,
                userLongitude = lng,
                markerTitle = "${effectiveLocation?.userName?.ifBlank { "User" } ?: "Child"} (Live)",
                uid = effectiveLocation?.uid?.ifBlank { userId } ?: userId,
                bearing = effectiveLocation?.bearing,
                accuracy = effectiveLocation?.accuracy ?: 0f,
                isLive = true,
                modifier = Modifier.fillMaxSize()
            )

            // Warning Banner if Device Location / GPS is toggled OFF
            if (isGpsOff) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = EmergencyRed.copy(alpha = 0.95f),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.LocationOff, contentDescription = null, tint = OnPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Device Location is OFF", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OnPrimary)
                            Text("Turn on GPS in phone settings for accurate fix", fontSize = 11.sp, color = OnPrimary.copy(alpha = 0.9f))
                        }
                        TextButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                context.startActivity(intent)
                            }
                        ) {
                            Text("ENABLE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }
                    }
                }
            } else if (!hasFix) {
                // Top Status Pill while acquiring satellite coordinate lock
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Acquiring GPS fix (updates every 10s)...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Floating Bottom Details Card
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.PersonPinCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = effectiveLocation?.userName?.ifBlank { "Live Tracker" } ?: "Live Tracker",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "UID: ${effectiveLocation?.uid?.ifBlank { userId } ?: userId} • Streaming every 10s",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (!effectiveLocation?.address.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                Icons.Filled.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = effectiveLocation?.address ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (hasFix) {
                            "Coordinates: ${String.format("%.5f", lat)}, ${String.format("%.5f", lng)} (±${effectiveLocation?.accuracy?.toInt() ?: 0}m)"
                        } else {
                            "Acquiring satellite coordinate fix..."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                MapUtils.openExternalMap(
                                    context = context,
                                    latitude = lat,
                                    longitude = lng,
                                    label = effectiveLocation?.userName?.ifBlank { "User Location" } ?: "User Location"
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("NAVIGATE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = OnPrimary)
                        }

                        OutlinedButton(
                            onClick = {
                                val shareText = "Live location of ${effectiveLocation?.userName ?: "Child"}: https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=16/$lat/$lng"
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Location"))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
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
