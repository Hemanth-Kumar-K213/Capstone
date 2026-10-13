package com.womensafety.system.data.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.womensafety.system.data.health.model.HealthConnectStatus
import com.womensafety.system.data.health.model.HealthDataFreshness
import com.womensafety.system.data.health.model.HeartRateMeasurement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Robust Health Connect client integration for Trinetra.
 * Provides safe, read-only access to Android Health Connect heart-rate records,
 * multi-user session isolation, and lifecycle-aware queries.
 */
object HealthConnectManager {

    private const val TAG = "HealthConnectManager"

    val HEART_RATE_PERMISSION: String = HealthPermission.getReadPermission(HeartRateRecord::class)

    // Cached states
    private val _latestMeasurement = MutableStateFlow<HeartRateMeasurement?>(null)
    val latestMeasurement: StateFlow<HeartRateMeasurement?> = _latestMeasurement.asStateFlow()

    private val _recentMeasurements = MutableStateFlow<List<HeartRateMeasurement>>(emptyList())
    val recentMeasurements: StateFlow<List<HeartRateMeasurement>> = _recentMeasurements.asStateFlow()

    private val _lastQueryTimestamp = MutableStateFlow<Long?>(null)
    val lastQueryTimestamp: StateFlow<Long?> = _lastQueryTimestamp.asStateFlow()

    private var activeSessionUserId: String? = null

    /**
     * Checks provider availability on the host device.
     */
    fun checkAvailability(context: Context): HealthConnectStatus {
        return try {
            val status = HealthConnectClient.getSdkStatus(context)
            when (status) {
                HealthConnectClient.SDK_AVAILABLE -> HealthConnectStatus.Available
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectStatus.UpdateRequired
                else -> HealthConnectStatus.Unavailable
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Health Connect status check failed", e)
            HealthConnectStatus.Error(e.message ?: "Unknown Health Connect provider error")
        }
    }

    /**
     * Validates whether read-only heart rate permission has been granted by the user.
     */
    suspend fun hasHeartRatePermission(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            if (checkAvailability(context) != HealthConnectStatus.Available) return@withContext false
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            granted.contains(HEART_RATE_PERMISSION)
        } catch (e: Throwable) {
            Log.e(TAG, "Permission check error", e)
            false
        }
    }

    /**
     * Queries a bounded recent time window for HeartRateRecord samples.
     * Guaranteed to run off the main thread.
     */
    suspend fun queryHeartRateRecords(
        context: Context,
        timeWindowHours: Long = 4
    ): Result<List<HeartRateMeasurement>> = withContext(Dispatchers.IO) {
        try {
            if (checkAvailability(context) != HealthConnectStatus.Available) {
                return@withContext Result.failure(IllegalStateException("Health Connect is not available on this device"))
            }

            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (!granted.contains(HEART_RATE_PERMISSION)) {
                return@withContext Result.failure(SecurityException("Health Connect heart-rate read permission not granted"))
            }

            val now = Instant.now()
            val startTime = now.minus(timeWindowHours, ChronoUnit.HOURS)

            val request = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, now),
                ascendingOrder = false
            )

            val response = client.readRecords(request)
            val samples = mutableListOf<HeartRateMeasurement>()

            for (record in response.records) {
                val originPackage = record.metadata.dataOrigin.packageName
                val recordId = record.metadata.id

                for (sample in record.samples) {
                    val bpm = sample.beatsPerMinute.toInt()
                    val timestamp = sample.time.toEpochMilli()
                    if (bpm > 0) {
                        samples.add(
                            HeartRateMeasurement(
                                bpm = bpm,
                                timestamp = timestamp,
                                dataOriginPackage = originPackage,
                                recordId = "${recordId}_$timestamp"
                            )
                        )
                    }
                }
            }

            // Deduplicate and sort newest first
            val sortedDistinct = samples
                .distinctBy { Pair(it.timestamp, it.bpm) }
                .sortedByDescending { it.timestamp }

            _recentMeasurements.value = sortedDistinct
            _latestMeasurement.value = sortedDistinct.firstOrNull()
            _lastQueryTimestamp.value = System.currentTimeMillis()

            Log.d(TAG, "Queried ${sortedDistinct.size} distinct heart-rate records. Latest: ${_latestMeasurement.value?.bpm} BPM")
            Result.success(sortedDistinct)
        } catch (e: Throwable) {
            Log.e(TAG, "Querying Health Connect failed", e)
            Result.failure(e)
        }
    }

    /**
     * Updates the active session user identity.
     * Enforces strict session isolation by clearing cached health records on account switch or logout.
     */
    fun onUserSessionChanged(newUserId: String?) {
        if (activeSessionUserId != newUserId) {
            Log.i(TAG, "User session changed from $activeSessionUserId to $newUserId. Clearing health caches.")
            activeSessionUserId = newUserId
            clearSession()
        }
    }

    /**
     * Clears all in-memory health caches and session state.
     */
    fun clearSession() {
        _latestMeasurement.value = null
        _recentMeasurements.value = emptyList()
        _lastQueryTimestamp.value = null
        Log.d(TAG, "Health Connect in-memory session cleared.")
    }
}
