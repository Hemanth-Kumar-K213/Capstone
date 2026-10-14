package com.womensafety.system.manager

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.womensafety.system.R
import com.womensafety.system.data.firebase.FirebaseAuthManager
import com.womensafety.system.data.model.AutoLearnStatus
import com.womensafety.system.data.model.RoutineLocation
import com.womensafety.system.data.model.UserLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Requirement 1A: Routine Auto-Learning Engine.
 *
 * Collects 30-day GPS history from userLocations/{userId}/locations,
 * clusters fixes (<100m), reverse-geocodes locations, computes frequencies
 * and arrival/departure time windows, and stores learned routines in
 * routines/{userId}/{dayOfWeek}/{locationId}.
 */
object RoutineAutoLearner {

    private const val TAG = "RoutineAutoLearner"
    private const val PREFS_NAME = "routine_auto_learn_prefs"
    private const val KEY_LAST_LEARN_TIME = "last_learn_time"
    private const val KEY_LOCATIONS_DISCOVERED = "locations_discovered"

    private const val CLUSTER_DISTANCE_METERS = 100.0
    private const val HISTORY_DAYS = 30
    private const val MIN_DAYS_FOR_LEARNING = 4

    private val db = FirebaseFirestore.getInstance()
    private val authManager = FirebaseAuthManager()
    private val scope = CoroutineScope(Dispatchers.IO)

    private var appContext: Context? = null

    private val _status = MutableStateFlow(AutoLearnStatus())
    val status: StateFlow<AutoLearnStatus> = _status.asStateFlow()

    fun initialize(context: Context) {
        this.appContext = context.applicationContext
        loadSavedStatus()
        scheduleDailyAlarm(context.applicationContext)
    }

    private fun loadSavedStatus() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastTime = prefs.getLong(KEY_LAST_LEARN_TIME, 0L)
        val count = prefs.getInt(KEY_LOCATIONS_DISCOVERED, 0)

        val nextScheduled = calculateNextSixAmTime()
        _status.value = AutoLearnStatus(
            lastAutoLearnTime = lastTime,
            locationsDiscovered = count,
            nextScheduledUpdate = nextScheduled,
            isCollecting = false,
            collectionDay = 4,
            message = if (lastTime > 0) "Last learned: ${formatTimeAgo(lastTime)}" else "Initial data collection active"
        )
    }

    private fun saveStatus(lastTime: Long, count: Int) {
        val ctx = appContext ?: return
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_LEARN_TIME, lastTime)
            .putInt(KEY_LOCATIONS_DISCOVERED, count)
            .apply()
    }

    /**
     * Checks if routine auto-learning is needed on app launch.
     * Triggers when today's routine does not exist or is > 7 days old.
     */
    fun checkAndTriggerOnLaunch(userId: String) {
        scope.launch {
            val today = LocalDate.now().dayOfWeek.name
            try {
                val snapshot = db.collection("routines")
                    .document(userId)
                    .collection(today)
                    .get()
                    .await()

                val shouldRun = if (snapshot.isEmpty) {
                    true
                } else {
                    val oldestTimestamp = snapshot.documents.mapNotNull {
                        it.getLong("learnedAt")
                    }.minOrNull() ?: 0L
                    val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
                    oldestTimestamp < sevenDaysAgo
                }

                if (shouldRun) {
                    Log.d(TAG, "Routine missing or > 7 days old. Triggering auto-learn.")
                    runAutoLearning(userId, isDailyJob = false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking routine status on launch", e)
            }
        }
    }

    /**
     * Manually requested or scheduled auto-learning execution.
     */
    fun triggerManualRefresh(onComplete: (Boolean, String) -> Unit = { _, _ -> }) {
        val userId = authManager.currentUserId ?: run {
            onComplete(false, "No active user logged in")
            return
        }
        scope.launch {
            val result = runAutoLearning(userId, isDailyJob = false)
            withContext(Dispatchers.Main) {
                onComplete(result.isSuccess, result.getOrDefault("Routine updated"))
            }
        }
    }

    /**
     * Core routine learning algorithm.
     */
    suspend fun runAutoLearning(userId: String, isDailyJob: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting 30-day GPS routine auto-learning for user: $userId")
            val cutoff = System.currentTimeMillis() - (HISTORY_DAYS.toLong() * 24 * 60 * 60 * 1000)

            // 1. Query GPS points from last 30 days
            val locationDocs = db.collection("userLocations")
                .document(userId)
                .collection("locations")
                .whereGreaterThanOrEqualTo("timestamp", cutoff)
                .get()
                .await()

            val locations = locationDocs.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                UserLocation.fromMap(doc.id, data)
            }

            Log.d(TAG, "Retrieved ${locations.size} location points from history")

            // 2. Evaluate days of data collection
            val distinctDays = locations.map { loc ->
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(loc.timestamp))
            }.distinct()

            val daysCount = distinctDays.size
            if (daysCount < MIN_DAYS_FOR_LEARNING) {
                val currentDay = (daysCount + 1).coerceAtMost(3)
                _status.value = AutoLearnStatus(
                    lastAutoLearnTime = System.currentTimeMillis(),
                    locationsDiscovered = 0,
                    nextScheduledUpdate = calculateNextSixAmTime(),
                    isCollecting = true,
                    collectionDay = currentDay,
                    message = "Collecting GPS data (Day $currentDay of 3). Auto-learning activates on Day 4."
                )
                Log.d(TAG, "Still in data collection phase (Day $currentDay of 3). Auto-learning skipped.")
                return@withContext Result.success("Data collection active (Day $currentDay of 3)")
            }

            // 3. Cluster GPS points across 30 days
            val clusters = clusterLocations(locations, CLUSTER_DISTANCE_METERS)
            Log.d(TAG, "Formed ${clusters.size} spatial clusters from GPS points")

            var totalSaved = 0
            val ctx = appContext

            // 4. For each day of the week, determine routine patterns from visits
            for (dayOfWeek in DayOfWeek.values()) {
                val dayName = dayOfWeek.name

                for ((clusterIndex, clusterPoints) in clusters.withIndex()) {
                    // Filter points belonging to this day of week
                    val dayPoints = clusterPoints.filter { pt ->
                        val cal = Calendar.getInstance().apply { timeInMillis = pt.timestamp }
                        cal.get(Calendar.DAY_OF_WEEK) == toCalendarDay(dayOfWeek)
                    }

                    if (dayPoints.isEmpty()) continue

                    // Calculate centroid
                    val avgLat = dayPoints.map { it.latitude }.average()
                    val avgLon = dayPoints.map { it.longitude }.average()

                    // Reverse geocode name
                    val resolvedName = resolveLocationName(ctx, avgLat, avgLon, dayPoints.firstOrNull()?.address)

                    // Visit frequency (how many distinct dates in 30 days)
                    val visitDates = dayPoints.map {
                        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it.timestamp))
                    }.distinct().size

                    // Time windows: arrival and departure times
                    val times = dayPoints.map { pt ->
                        val cal = Calendar.getInstance().apply { timeInMillis = pt.timestamp }
                        cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                    }.sorted()

                    val arrivalMins = times.first()
                    val departureMins = times.last()
                    val arrivalTimeStr = String.format(Locale.US, "%02d:%02d", arrivalMins / 60, arrivalMins % 60)
                    val departureTimeStr = String.format(Locale.US, "%02d:%02d", departureMins / 60, departureMins % 60)

                    // Confidence score based on visit frequency consistency
                    val confidence = (0.70f + (visitDates.toFloat() / 4.0f) * 0.25f).coerceIn(0.70f, 0.98f)

                    val locationId = "loc_auto_${clusterIndex + 1}"
                    val learnedLocation = RoutineLocation(
                        locationId = locationId,
                        name = resolvedName,
                        lat = avgLat,
                        lon = avgLon,
                        radiusMeters = 100f,
                        visitFrequency = visitDates,
                        typicalArrivalTime = arrivalTimeStr,
                        typicalDepartureTime = departureTimeStr,
                        confidence = confidence,
                        learnedAt = System.currentTimeMillis(),
                        source = "auto-learned"
                    )

                    // Write to routines/{userId}/{dayOfWeek}/{locationId}
                    db.collection("routines")
                        .document(userId)
                        .collection(dayName)
                        .document(locationId)
                        .set(learnedLocation.toMap())
                        .await()

                    totalSaved++
                }
            }

            val now = System.currentTimeMillis()
            val discoveredCount = clusters.size
            saveStatus(now, discoveredCount)

            _status.value = AutoLearnStatus(
                lastAutoLearnTime = now,
                locationsDiscovered = discoveredCount,
                nextScheduledUpdate = calculateNextSixAmTime(),
                isCollecting = false,
                collectionDay = 4,
                message = "Learned $discoveredCount routine locations across 7 days"
            )

            // Day 4 onwards: Show notification when routine is learned
            if (!isDailyJob || discoveredCount > 0) {
                showRoutineLearnedNotification(ctx, discoveredCount)
            }

            Log.d(TAG, "Auto-learning finished: saved $totalSaved daily routine records")
            Result.success("Discovered $discoveredCount routine locations")
        } catch (e: Exception) {
            Log.e(TAG, "Auto-learning routine failed", e)
            Result.failure(e)
        }
    }

    /**
     * Proximity-based clustering (distance < 100m).
     */
    private fun clusterLocations(locations: List<UserLocation>, maxDistanceMeters: Double): List<List<UserLocation>> {
        val clusters = mutableListOf<MutableList<UserLocation>>()

        for (loc in locations) {
            var added = false
            for (cluster in clusters) {
                val rep = cluster.first()
                if (haversineDistance(loc.latitude, loc.longitude, rep.latitude, rep.longitude) < maxDistanceMeters) {
                    cluster.add(loc)
                    added = true
                    break
                }
            }
            if (!added) {
                clusters.add(mutableListOf(loc))
            }
        }

        // Only keep locations visited more than once
        return clusters.filter { it.size >= 2 }
    }

    private fun resolveLocationName(context: Context?, lat: Double, lon: Double, fallbackAddress: String?): String {
        if (context != null) {
            try {
                @Suppress("DEPRECATION")
                val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)
                val addr = addresses?.firstOrNull()
                if (addr != null) {
                    val feature = addr.featureName
                    val thoroughfare = addr.thoroughfare
                    val locality = addr.locality ?: addr.subAdminArea
                    return when {
                        !feature.isNullOrBlank() && feature != thoroughfare -> "$feature, $locality"
                        !thoroughfare.isNullOrBlank() -> "$thoroughfare, $locality"
                        !locality.isNullOrBlank() -> locality
                        else -> addr.getAddressLine(0) ?: "Routine Location"
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Geocoder lookup failed: ${e.message}")
            }
        }

        if (!fallbackAddress.isNullOrBlank() && !fallbackAddress.startsWith("Resolving")) {
            return fallbackAddress.split(",").take(2).joinToString(",").trim()
        }

        return "Routine Anchor (${String.format(Locale.US, "%.3f", lat)}, ${String.format(Locale.US, "%.3f", lon)})"
    }

    private fun showRoutineLearnedNotification(context: Context?, locationCount: Int) {
        val ctx = context ?: return
        try {
            val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channelId = com.womensafety.system.utils.NotificationChannelManager.CHANNEL_SAFETY_CONFIRMATION

            val notification = NotificationCompat.Builder(ctx, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("New Routine Learned!")
                .setContentText("Trinetra identified $locationCount routine anchor locations from your travel patterns.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(7721, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to show routine learned notification", e)
        }
    }

    private fun scheduleDailyAlarm(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, RoutineAutoLearnReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                8831,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerTime = calculateNextSixAmTime()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            Log.d(TAG, "Scheduled next daily 06:00 AM auto-learn alarm at: ${Date(triggerTime)}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule daily auto-learn alarm", e)
        }
    }

    private fun calculateNextSixAmTime(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun toCalendarDay(dayOfWeek: DayOfWeek): Int {
        return when (dayOfWeek) {
            DayOfWeek.SUNDAY -> Calendar.SUNDAY
            DayOfWeek.MONDAY -> Calendar.MONDAY
            DayOfWeek.TUESDAY -> Calendar.TUESDAY
            DayOfWeek.WEDNESDAY -> Calendar.WEDNESDAY
            DayOfWeek.THURSDAY -> Calendar.THURSDAY
            DayOfWeek.FRIDAY -> Calendar.FRIDAY
            DayOfWeek.SATURDAY -> Calendar.SATURDAY
        }
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun formatTimeAgo(time: Long): String {
        val diff = System.currentTimeMillis() - time
        val mins = diff / (60 * 1000)
        val hours = mins / 60
        val days = hours / 24
        return when {
            mins < 5 -> "Just now"
            mins < 60 -> "$mins minutes ago"
            hours < 24 -> "$hours hours ago"
            else -> "$days days ago"
        }
    }
}

/**
 * Receiver for 06:00 AM daily routine auto-learning.
 */
class RoutineAutoLearnReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("RoutineAutoLearnReceiver", "06:00 AM alarm fired. Running auto-learn.")
        val authManager = FirebaseAuthManager()
        val userId = authManager.currentUserId ?: return
        RoutineAutoLearner.initialize(context)
        CoroutineScope(Dispatchers.IO).launch {
            RoutineAutoLearner.runAutoLearning(userId, isDailyJob = true)
        }
    }
}
