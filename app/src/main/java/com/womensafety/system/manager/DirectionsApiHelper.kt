package com.womensafety.system.manager

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Helper to fetch road routing waypoints using OpenStreetMap / OSRM (Open Source Routing Machine).
 * 100% Free and Open Source with no API key or billing required.
 * Gracefully falls back to realistic simulated road routes when offline.
 */
object DirectionsApiHelper {
    private const val TAG = "DirectionsApiHelper"

    /**
     * Fetches actual road route coordinates using OSRM (OpenStreetMap Routing Machine).
     * Falls back to simulated road waypoints if network is unavailable.
     */
    suspend fun getRoute(context: Context, start: GeoPoint, dest: GeoPoint): List<GeoPoint> = withContext(Dispatchers.IO) {
        val osrmUrl = "https://router.project-osrm.org/route/v1/driving/" +
                "${start.longitude},${start.latitude};${dest.longitude},${dest.latitude}" +
                "?overview=full&geometries=geojson"

        try {
            val url = URL(osrmUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", context.packageName)
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val routePoints = parseOsrmGeoJson(response)
                if (routePoints.isNotEmpty()) {
                    Log.i(TAG, "Successfully fetched OSRM OpenStreetMap route of size ${routePoints.size}.")
                    return@withContext routePoints
                }
            } else {
                Log.w(TAG, "OSRM route request returned HTTP status code: $responseCode")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network route fetch failed (${e.message}). Falling back to simulated road route.")
        }

        return@withContext generateSimulatedRoadRoute(start, dest)
    }

    /**
     * Parses GeoJSON coordinates [longitude, latitude] returned by OSRM into GeoPoint list.
     */
    private fun parseOsrmGeoJson(jsonStr: String): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        try {
            val json = JSONObject(jsonStr)
            val routes = json.optJSONArray("routes") ?: return emptyList()
            if (routes.length() == 0) return emptyList()

            val primaryRoute = routes.getJSONObject(0)
            val geometry = primaryRoute.optJSONObject("geometry") ?: return emptyList()
            val coordinates = geometry.optJSONArray("coordinates") ?: return emptyList()

            for (i in 0 until coordinates.length()) {
                val coord = coordinates.getJSONArray(i)
                val lon = coord.getDouble(0)
                val lat = coord.getDouble(1)
                points.add(GeoPoint(lat, lon))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing OSRM GeoJSON response: ${e.message}", e)
        }
        return points
    }

    /**
     * Generates simulated realistic road curves between start and destination coordinates.
     */
    private fun generateSimulatedRoadRoute(start: GeoPoint, dest: GeoPoint): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        points.add(start)

        val latDiff = dest.latitude - start.latitude
        val lngDiff = dest.longitude - start.longitude

        val segments = 7
        for (i in 1 until segments) {
            val fraction = i.toDouble() / segments
            val segmentPoint = if (i % 2 == 0) {
                GeoPoint(start.latitude + latDiff * fraction, start.longitude + lngDiff * (fraction - 0.03))
            } else {
                GeoPoint(start.latitude + latDiff * (fraction - 0.03), start.longitude + lngDiff * fraction)
            }
            points.add(segmentPoint)
        }

        points.add(dest)
        return points
    }
}
