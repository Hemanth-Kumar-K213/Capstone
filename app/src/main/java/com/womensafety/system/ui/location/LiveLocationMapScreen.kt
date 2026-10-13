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
import com.google.firebase.auth.FirebaseAuth
import com.womensafety.system.data.model.AccountType
import com.womensafety.system.data.model.UserLocation
import com.womensafety.system.data.repository.AuthRepository
import com.womensafety.system.data.repository.UidRepository
import com.womensafety.system.manager.TrinetraLocationManager
import com.womensafety.system.service.LocationTrackingService
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.components.PrimaryButton
import com.womensafety.system.ui.components.SecondaryButton
import com.womensafety.system.ui.map.SafetyMapTracker
import com.womensafety.system.ui.theme.*
import com.womensafety.system.utils.DateUtils
import com.womensafety.system.utils.MapUtils
import com.womensafety.system.utils.Validators
import com.womensafety.system.viewmodel.LocationViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Live Location Map Screen using OpenStreetMap (OSMDroid).
 * 100% Free & Open-Source (No Google Maps API keys or billing needed).
 *
 * For Trusted Contacts viewing a protected user:
 * - Fetches real-time GPS coordinates directly and strictly from Firebase.
 * - Displays the protected user's active UID prominently.
 * - NEVER tracks or shows the Trusted Contact's local device GPS.
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
    val authRepository = remember { AuthRepository() }
    val uidRepository = remember { UidRepository() }

    val currentAuthUserId = remember { FirebaseAuth.getInstance().currentUser?.uid }
    var isViewingSelf by remember { mutableStateOf(currentAuthUserId != null && currentAuthUserId == userId) }
    var resolvedUid by remember(userId) { mutableStateOf<String?>(null) }

    // Stream coordinates directly from Firebase for this specific user / UID
    val liveLocation by viewModel.observeLocationByUserIdOrUid(userId).collectAsState(initial = null)
    val localServiceLocation by LocationTrackingService.currentLocation.collectAsState()
    var immediateFix by remember { mutableStateOf<UserLocation?>(null) }
    var isGpsOff by remember { mutableStateOf(false) }

    // Resolve whether the viewer is viewing their own phone or monitoring a protected user (Trusted Contact)
    LaunchedEffect(userId, currentAuthUserId) {
        val currentUid = currentAuthUserId
        if (currentUid == null) {
            isViewingSelf = false
            return@LaunchedEffect
        }
        if (currentUid == userId) {
            isViewingSelf = true
            return@LaunchedEffect
        }

        val accountType = withTimeoutOrNull(3000L) {
            authRepository.getCurrentAccountType().getOrNull()
        }
        if (accountType == AccountType.TRUSTED_CONTACT) {
            isViewingSelf = false
            return@LaunchedEffect
        }

        val ownUid = withTimeoutOrNull(3000L) {
            uidRepository.getCurrentUid(currentUid).getOrNull()
        }
        isViewingSelf = (ownUid != null && ownUid.uid.isNotBlank() && ownUid.uid == userId)
    }

    // Resolve the protected user's active Safety UID if userId was provided as an Auth UID
    LaunchedEffect(userId) {
        if (Validators.isValidUidFormat(userId) || userId.startsWith("TRN-", ignoreCase = true)) {
            resolvedUid = userId
        } else {
            val fetched = withTimeoutOrNull(5000L) {
                uidRepository.getCurrentUid(userId).getOrNull()
            }
            if (fetched != null && fetched.uid.isNotBlank()) {
                resolvedUid = fetched.uid
            }
        }
    }

    // Launcher for runtime location permissions (ONLY invoked when user is viewing their OWN location)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (!isViewingSelf) return@rememberLauncherForActivityResult
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

    // Local device hardware GPS: ONLY active when viewing self!
    LaunchedEffect(isViewingSelf) {
        if (isViewingSelf) {
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
            isGpsOff = !TrinetraLocationManager.isGpsEnabled(context)
        } else {
            isGpsOff = false
        }
    }

    // Coordinate resolution:
    // If viewing self: can fall back to local service coordinates
    // If viewing remote user (Trusted Contact mode): STRICTLY from Firebase liveLocation! Never local device GPS!
    val effectiveLocation = if (isViewingSelf) {
        liveLocation ?: localServiceLocation ?: immediateFix
    } else {
        liveLocation
    }

    val hasFix = effectiveLocation != null && (effectiveLocation.latitude != 0.0 || effectiveLocation.longitude != 0.0)
    val lat = if (hasFix) effectiveLocation!!.latitude else 12.9716
    val lng = if (hasFix) effectiveLocation!!.longitude else 77.5946

    val displayUid = effectiveLocation?.uid?.ifBlank { null }
        ?: resolvedUid
        ?: userId

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = effectiveLocation?.userName?.ifBlank { if (isViewingSelf) "My Radar Map" else "Protected User Location" }
                                ?: if (isViewingSelf) "My Radar Map" else "Protected User Location",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = if (hasFix) "UID: $displayUid • 10s GPS Sync Active" else "UID: $displayUid • Fetching from Firebase...",
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
                userLatitude = if (hasFix) lat else 0.0,
                userLongitude = if (hasFix) lng else 0.0,
                markerTitle = "${effectiveLocation?.userName?.ifBlank { "Protected User" } ?: "Protected User"} (Live)",
                uid = displayUid,
                bearing = effectiveLocation?.bearing,
                accuracy = effectiveLocation?.accuracy ?: 0f,
                isLive = true,
                modifier = Modifier.fillMaxSize()
            )

            // Warning Banner if Device Location / GPS is toggled OFF (ONLY shown when viewing self)
            if (isViewingSelf && isGpsOff) {
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
                // Top Status Pill while acquiring satellite coordinate lock from Firebase
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
                            text = "Fetching live coordinates for UID: $displayUid from Firebase...",
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
                                text = effectiveLocation?.userName?.ifBlank { if (isViewingSelf) "My Location" else "Protected User" }
                                    ?: if (isViewingSelf) "My Location" else "Protected User",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "UID: $displayUid • Streaming every 10s",
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
                            "Connecting to Firebase satellite feed for UID: $displayUid..."
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
                                if (hasFix) {
                                    MapUtils.openExternalMap(
                                        context = context,
                                        latitude = lat,
                                        longitude = lng,
                                        label = effectiveLocation?.userName?.ifBlank { "User Location" } ?: "User Location"
                                    )
                                }
                            },
                            enabled = hasFix,
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
                                val shareText = if (hasFix) {
                                    "Live location of ${effectiveLocation?.userName ?: "Protected User"} (UID: $displayUid): https://www.openstreetmap.org/?mlat=$lat&mlon=$lng#map=16/$lat/$lng"
                                } else {
                                    "Protected User UID: $displayUid (Awaiting live GPS coordinates from Firebase)"
                                }
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
