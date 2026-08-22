package com.womensafety.system.ui.shake

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.womensafety.system.data.model.ShakeSensitivity
import com.womensafety.system.ui.home.GlassCard
import com.womensafety.system.ui.theme.*
import com.womensafety.system.viewmodel.ShakeDetectionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShakeDetectionSettingsScreen(
    viewModel: ShakeDetectionViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isShakeActive by viewModel.isShakeActive.collectAsState()
    val currentSensitivity by viewModel.currentSensitivity.collectAsState()
    val isTestModeActive by viewModel.isTestModeActive.collectAsState()

    var testShakeDetectedMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopTestMode(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Shake Detection Settings",
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
                text = "ACCELEROMETER SHAKE SENSOR",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                color = GradientGold,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Shake your smartphone vigorously to trigger an emergency SOS alert immediately, even when the screen is off.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariantDark
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Service Status Card
            GlassCard(
                accentGradient = Brush.horizontalGradient(
                    colors = listOf(
                        GradientRose.copy(alpha = 0.08f),
                        GradientMagenta.copy(alpha = 0.04f)
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
                                    if (isShakeActive) StatusApproved.copy(alpha = 0.15f)
                                    else StatusDenied.copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Vibration,
                                contentDescription = null,
                                tint = if (isShakeActive) StatusApproved else StatusDenied,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Shake Sensor Status",
                                style = MaterialTheme.typography.labelMedium,
                                color = OnSurfaceVariantDark
                            )
                            Text(
                                if (isShakeActive) "Active (Monitoring)" else "Inactive",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isShakeActive) StatusApproved else OnSurfaceDark
                            )
                        }

                        Switch(
                            checked = isShakeActive,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    viewModel.startShakeDetection(context)
                                } else {
                                    viewModel.stopShakeDetection(context)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnPrimary,
                                checkedTrackColor = GradientAmber
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sensitivity Levels
            GlassCard {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Sensitivity Threshold",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Choose how hard you must shake your phone to activate SOS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantDark
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    ShakeSensitivity.entries.forEach { sensitivity ->
                        val isSelected = currentSensitivity == sensitivity
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    viewModel.updateSensitivity(context, sensitivity)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.updateSensitivity(context, sensitivity) },
                                colors = RadioButtonDefaults.colors(selectedColor = GradientAmber)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    sensitivity.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) GradientAmber else OnSurfaceDark
                                )
                                Text(
                                    sensitivity.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariantDark
                                )
                            }
                        }

                        if (sensitivity != ShakeSensitivity.HIGH) {
                            HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Interactive Test Shake Area
            GlassCard(
                accentGradient = Brush.horizontalGradient(
                    colors = listOf(
                        GradientAmber.copy(alpha = 0.1f),
                        GradientGold.copy(alpha = 0.05f)
                    )
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.TouchApp, contentDescription = null, tint = GradientAmber)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Test Shake Sensitivity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceDark
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        "Activate test mode and shake your phone to verify detection without triggering real emergency alerts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantDark
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isTestModeActive) {
                        Button(
                            onClick = {
                                viewModel.stopTestMode(context)
                                testShakeDetectedMessage = null
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusDenied),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("STOP TEST MODE", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                testShakeDetectedMessage = null
                                viewModel.startTestMode(context) {
                                    testShakeDetectedMessage = "✓ Shake Detected at ${currentSensitivity.displayName}!"
                                    Toast.makeText(context, "Shake Detected!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GradientAmber),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("START TEST MODE", fontWeight = FontWeight.Bold)
                        }
                    }

                    AnimatedVisibility(
                        visible = testShakeDetectedMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            color = StatusApproved.copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = StatusApproved)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    testShakeDetectedMessage ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusApproved
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
