package com.womensafety.system.ui.safety

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.location.LocationServices
import com.womensafety.system.data.model.RoutineLocation
import com.womensafety.system.ui.theme.CoralRed
import com.womensafety.system.ui.theme.EmergencyRed
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * Requirement 1B: Routine Manual Entry Dialog.
 *
 * Allows adding or modifying routine locations with:
 * - Location Name
 * - Latitude / Longitude (with GPS fix button)
 * - Radius slider (50m - 500m)
 * - Arrival & Departure time pickers
 * - Days of week checkboxes (Mon - Sun)
 * - Instant persistence to Firestore routines/{userId}/{dayOfWeek}/{locationId}
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineManualEntryDialog(
    initialLocation: RoutineLocation? = null,
    initialDayOfWeek: DayOfWeek = LocalDate.now().dayOfWeek,
    onDismiss: () -> Unit,
    onSaveLocation: (days: List<DayOfWeek>, location: RoutineLocation) -> Unit,
    onDeleteLocation: ((dayOfWeek: DayOfWeek, locationId: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val isEditMode = initialLocation != null

    var name by remember { mutableStateOf(initialLocation?.name ?: "") }
    var latText by remember { mutableStateOf(initialLocation?.let { String.format(Locale.US, "%.6f", it.lat) } ?: "") }
    var lonText by remember { mutableStateOf(initialLocation?.let { String.format(Locale.US, "%.6f", it.lon) } ?: "") }
    var radiusMeters by remember { mutableFloatStateOf(initialLocation?.radiusMeters ?: 100f) }
    var arrivalTime by remember { mutableStateOf(initialLocation?.typicalArrivalTime ?: "09:00") }
    var departureTime by remember { mutableStateOf(initialLocation?.typicalDepartureTime ?: "17:00") }

    val allDays = DayOfWeek.values().toList()
    val selectedDays = remember {
        mutableStateMapOf<DayOfWeek, Boolean>().apply {
            allDays.forEach { day ->
                put(day, if (isEditMode) day == initialDayOfWeek else (day == initialDayOfWeek))
            }
        }
    }

    // Helper for native TimePickerDialog
    fun showTimePicker(currentTimeStr: String, onTimeSelected: (String) -> Unit) {
        val parts = currentTimeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                val formatted = String.format(Locale.US, "%02d:%02d", selectedHour, selectedMinute)
                onTimeSelected(formatted)
            },
            hour,
            minute,
            true
        ).show()
    }

    // Current GPS coordinates fetcher
    fun fetchCurrentGpsCoordinates() {
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    latText = String.format(Locale.US, "%.6f", loc.latitude)
                    lonText = String.format(Locale.US, "%.6f", loc.longitude)
                    Toast.makeText(context, "Current GPS location set", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "GPS location unavailable. Ensure GPS is on.", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener {
                Toast.makeText(context, "Failed to get GPS location: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error reading GPS", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isEditMode) Icons.Filled.EditLocation else Icons.Filled.AddLocationAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isEditMode) "Edit Routine Location" else "Add Custom Location",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // Scrollable Form Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Location Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Location Name") },
                        placeholder = { Text("e.g., Home, Tech Park, City Gym") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) }
                    )

                    // Latitude & Longitude
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = latText,
                            onValueChange = { latText = it },
                            label = { Text("Latitude") },
                            placeholder = { Text("12.9716") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = lonText,
                            onValueChange = { lonText = it },
                            label = { Text("Longitude") },
                            placeholder = { Text("77.5946") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // GPS Pin / Current Location Button
                    OutlinedButton(
                        onClick = { fetchCurrentGpsCoordinates() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Use Current GPS Location")
                    }

                    // Radius Slider (50m - 500m)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Geofence Radius",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${radiusMeters.toInt()} meters",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = radiusMeters,
                            onValueChange = { radiusMeters = it },
                            valueRange = 50f..500f,
                            steps = 9
                        )
                    }

                    // Time Windows: Arrival & Departure
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showTimePicker(arrivalTime) { arrivalTime = it } },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Typical Arrival", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(arrivalTime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showTimePicker(departureTime) { departureTime = it } },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Typical Departure", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(departureTime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Days of Week Selection
                    Column {
                        Text(
                            "Active Days of Week",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val daysAbbr = listOf(
                            DayOfWeek.MONDAY to "Mon",
                            DayOfWeek.TUESDAY to "Tue",
                            DayOfWeek.WEDNESDAY to "Wed",
                            DayOfWeek.THURSDAY to "Thu",
                            DayOfWeek.FRIDAY to "Fri",
                            DayOfWeek.SATURDAY to "Sat",
                            DayOfWeek.SUNDAY to "Sun"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            daysAbbr.forEach { (day, label) ->
                                val isSelected = selectedDays[day] == true
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedDays[day] = !isSelected },
                                    label = { Text(label, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // Actions Footer: Delete (if editing), Cancel, Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEditMode && onDeleteLocation != null && initialLocation != null) {
                        TextButton(
                            onClick = {
                                onDeleteLocation(initialDayOfWeek, initialLocation.locationId)
                                Toast.makeText(context, "Location removed from routine", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = CoralRed)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val parsedLat = latText.toDoubleOrNull()
                            val parsedLon = lonText.toDoubleOrNull()
                            val targetDays = selectedDays.filter { it.value }.keys.toList()

                            when {
                                name.isBlank() -> {
                                    Toast.makeText(context, "Please enter a location name", Toast.LENGTH_SHORT).show()
                                }
                                parsedLat == null || parsedLon == null -> {
                                    Toast.makeText(context, "Please enter valid latitude and longitude", Toast.LENGTH_SHORT).show()
                                }
                                targetDays.isEmpty() -> {
                                    Toast.makeText(context, "Please select at least one active day", Toast.LENGTH_SHORT).show()
                                }
                                else -> {
                                    val locId = initialLocation?.locationId ?: "loc_${UUID.randomUUID()}"
                                    val newLocation = RoutineLocation(
                                        locationId = locId,
                                        name = name.trim(),
                                        lat = parsedLat,
                                        lon = parsedLon,
                                        radiusMeters = radiusMeters,
                                        visitFrequency = initialLocation?.visitFrequency ?: targetDays.size,
                                        typicalArrivalTime = arrivalTime,
                                        typicalDepartureTime = departureTime,
                                        confidence = 1.0f,
                                        learnedAt = System.currentTimeMillis(),
                                        source = "manual"
                                    )
                                    onSaveLocation(targetDays, newLocation)
                                    Toast.makeText(context, "Custom location saved to routine", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Location")
                    }
                }
            }
        }
    }
}
