package com.womensafety.system.manager

import android.content.Context
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayList

object DirectionsApiHelper {
    private const val TAG = "DirectionsApiHelper"

    /**
     * Fetches actual road route coordinates from Google Directions API.
     * Falls back to a simulated road route if the API key is not configured or network fails.
     */
    suspend fun getRoute(context: Context, start: LatLng, dest: LatLng): List<LatLng> = withContext(Dispatchers.IO) {
        val apiKey = try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString("com.google.android.geo.API_KEY")
        } catch (e: Exception) {
            null
        }

        if (apiKey.isNullOrBlank() || apiKey.contains("PLACEHOLDER") || apiKey == "YOUR_API_KEY") {
            Log.w(TAG, "No valid Google Maps API key configured. Generating a simulated multi-turn road route.")
            return@withContext generateSimulatedRoadRoute(start, dest)
        }

        val urlString = "https://maps.googleapis.com/maps/api/directions/json" +
                "?origin=${start.latitude},${start.longitude}" +
                "&destination=${dest.latitude},${dest.longitude}" +
                "&key=$apiKey"

        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val routePoints = parseDirectionsJson(response)
                if (routePoints.isNotEmpty()) {
                    Log.i(TAG, "Successfully fetched actual road route of size ${routePoints.size} from Google Directions API.")
                    return@withContext routePoints
                }
            } else {
                Log.w(TAG, "Google Directions API request failed with HTTP status code: $responseCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching road route from Google Directions API: ${e.message}", e)
        }

        Log.w(TAG, "Falling back to simulated multi-turn road route.")
        return@withContext generateSimulatedRoadRoute(start, dest)
    }

    private fun parseDirectionsJson(json: String): List<LatLng> {
        val points = mutableListOf<LatLng>()
        try {
            val polylineKey = "\"overview_polyline\""
            val pointsKey = "\"points\""
            val index = json.indexOf(polylineKey)
            if (index != -1) {
                val pointsIndex = json.indexOf(pointsKey, index)
                if (pointsIndex != -1) {
                    val startQuote = json.indexOf('"', pointsIndex + pointsKey.length + 1)
                    val endQuote = json.indexOf('"', startQuote + 1)
                    if (startQuote != -1 && endQuote != -1) {
                        val encodedPoints = json.substring(startQuote + 1, endQuote)
                        val unescaped = encodedPoints.replace("\\\\", "\\")
                        return decodePoly(unescaped)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing directions JSON: ${e.message}", e)
        }
        return points
    }

    private fun decodePoly(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result ushr 1).inv() else result ushr 1
            lat += dlat
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result ushr 1).inv() else result ushr 1
            lng += dlng
            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }
        return poly
    }

    private fun generateSimulatedRoadRoute(start: LatLng, dest: LatLng): List<LatLng> {
        val points = mutableListOf<LatLng>()
        points.add(start)

        val latDiff = dest.latitude - start.latitude
        val lngDiff = dest.longitude - start.longitude

        // Generate 6 turns (7 segments) to look like actual city roads
        val segments = 7
        for (i in 1 until segments) {
            val fraction = i.toDouble() / segments
            val segmentPoint = if (i % 2 == 0) {
                LatLng(start.latitude + latDiff * fraction, start.longitude + lngDiff * (fraction - 0.03))
            } else {
                LatLng(start.latitude + latDiff * (fraction - 0.03), start.longitude + lngDiff * fraction)
            }
            points.add(segmentPoint)
        }

        points.add(dest)
        return points
    }
}
