package com.womensafety.system.ui.safety

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.womensafety.system.ui.components.AppCard
import com.womensafety.system.ui.theme.*
import java.util.Locale
import kotlin.math.sqrt

/**
 * Requirement 3A: Accelerometer Diagnostic Screen.
 *
 * Real-time diagnostic monitor to verify live hardware accelerometer streaming:
 * - X, Y, Z acceleration (m/s²)
 * - Raw Magnitude: √(X² + Y² + Z²)
 * - Gravity-Adjusted: √(X² + Y² + Z²) - 9.8
 * - Live Waveform Canvas (rolling history of last 10 seconds)
 * - Throttled Logcat output every 50ms
 * - Free-fall and Impact transient indicators
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccelerometerDiagnosticScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val accelerometer = remember { sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    var xVal by remember { mutableFloatStateOf(0f) }
    var yVal by remember { mutableFloatStateOf(0f) }
    var zVal by remember { mutableFloatStateOf(0f) }
    var magnitude by remember { mutableFloatStateOf(9.8f) }
    var adjusted by remember { mutableFloatStateOf(0.0f) }
    var peakAdjusted by remember { mutableFloatStateOf(0.0f) }
    var eventCount by remember { mutableLongStateOf(0L) }
    var isStreaming by remember { mutableStateOf(false) }

    // History buffer of last 100 samples (~10 seconds at ~10Hz UI sampling)
    val history = remember { mutableStateListOf<Float>() }

    DisposableEffect(Unit) {
        if (sensorManager != null && accelerometer != null) {
            var lastLogTime = 0L

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
                    isStreaming = true

                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]

                    val mag = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                    val adj = mag - 9.80665f

                    xVal = x
                    yVal = y
                    zVal = z
                    magnitude = mag
                    adjusted = adj
                    if (adj > peakAdjusted) {
                        peakAdjusted = adj
                    }
                    eventCount++

                    // Add to rolling history buffer
                    if (history.size >= 120) {
                        history.removeAt(0)
                    }
                    history.add(adj)

                    // Logcat output every 50ms
                    val now = System.currentTimeMillis()
                    if (now - lastLogTime >= 50L) {
                        lastLogTime = now
                        Log.d(
                            "AccelDiag",
                            String.format(
                                Locale.US,
                                "X: %.2f, Y: %.2f, Z: %.2f | Mag: %.2f | Adj: %.2f | Peak: %.2f",
                                x, y, z, mag, adj, peakAdjusted
                            )
                        )
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            sensorManager.registerListener(
                listener,
                accelerometer,
                SensorManager.SENSOR_DELAY_GAME
            )

            onDispose {
                sensorManager.unregisterListener(listener)
                isStreaming = false
            }
        } else {
            onDispose {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Accelerometer Diagnostic",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { peakAdjusted = 0.0f }) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = "Reset Peak")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hardware Sensor Status Banner
            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (isStreaming) StatusApproved else EmergencyRed)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isStreaming) "STREAMING ACTIVE (50Hz)" else "HARDWARE SENSOR INACTIVE",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isStreaming) StatusApproved else EmergencyRed
                        )
                        Text(
                            text = "${accelerometer?.name ?: "Unknown Sensor"} • Events received: $eventCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Real-Time Acceleration Readings
            AppCard {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("LIVE ACCELERATION READINGS", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AxisItem(label = "X-Axis", value = xVal, unit = "m/s²")
                        AxisItem(label = "Y-Axis", value = yVal, unit = "m/s²")
                        AxisItem(label = "Z-Axis", value = zVal, unit = "m/s²")
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Raw Magnitude √(X²+Y²+Z²)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                String.format(Locale.US, "%.2f m/s²", magnitude),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Gravity-Adjusted (Mag - 9.8)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val adjColor = when {
                                adjusted > 25.0f -> EmergencyRed
                                adjusted < -6.5f -> AmberOrange
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Text(
                                String.format(Locale.US, "%+.2f m/s²", adjusted),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = adjColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Peak Spike Recorded
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Peak Impact Recorded:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            String.format(Locale.US, "%.2f m/s²", peakAdjusted),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (peakAdjusted > 25.0f) EmergencyRed else StatusApproved
                        )
                    }
                }
            }

            // Real-Time Waveform Chart
            AppCard {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ACCELERATION WAVEFORM (LAST 10s)", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Scale: -10 to +40 m/s²", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.85f))
                            .padding(8.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            // Draw baseline (adjusted = 0)
                            val zeroY = h * 0.75f
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.4f),
                                start = Offset(0f, zeroY),
                                end = Offset(w, zeroY),
                                strokeWidth = 1.dp.toPx()
                            )

                            // Draw impact threshold line (+25 m/s²)
                            val impactY = zeroY - (25f / 50f) * h
                            drawLine(
                                color = Color.Red.copy(alpha = 0.5f),
                                start = Offset(0f, impactY),
                                end = Offset(w, impactY),
                                strokeWidth = 1.dp.toPx()
                            )

                            // Draw waveform
                            if (history.size >= 2) {
                                val path = Path()
                                val stepX = w / (history.size - 1).coerceAtLeast(1)

                                history.forEachIndexed { i, valAdj ->
                                    val clamped = valAdj.coerceIn(-10f, 40f)
                                    val y = zeroY - (clamped / 50f) * h
                                    val x = i * stepX
                                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }

                                drawPath(
                                    path = path,
                                    color = Color.Cyan,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }

            // Phase State & Drop Test Instructions
            AppCard {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("FALL DETECTION PHASE TESTER", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(10.dp))

                    val isFreeFall = magnitude < 3.0f || adjusted < -6.8f
                    val isImpact = adjusted > 25.0f

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PhaseBadge(label = "PHASE A: Free-Fall", active = isFreeFall, color = AmberOrange, modifier = Modifier.weight(1f))
                        PhaseBadge(label = "PHASE B: Impact", active = isImpact, color = EmergencyRed, modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("How to test on a real phone:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Text("1. Hold phone flat: Gravity-adjusted should show ≈ 0.0 m/s²", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("2. Drop phone safely on a bed (30cm drop): Watch Phase A activate during free-fall, followed immediately by Phase B (> 25 m/s² impact spike).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("3. Check logcat: Tag 'AccelDiag' streams readings every 50ms.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AxisItem(label: String, value: Float, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            String.format(Locale.US, "%+.2f", value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(unit, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PhaseBadge(label: String, active: Boolean, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = if (active) color.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        border = if (active) androidx.compose.foundation.BorderStroke(1.5.dp, color) else null
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (active) color else Color.Gray)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
