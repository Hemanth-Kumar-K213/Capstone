package com.womensafety.system.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.osmdroid.util.GeoPoint
import kotlin.math.*

/**
 * Common map and geographical utility functions for OpenStreetMap (OSMDroid).
 */
object MapUtils {

    /**
     * Earth's radius in kilometers.
     */
    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Converts a list of coordinate pairs or custom points to OSMDroid [GeoPoint] list.
     */
    fun toGeoPoints(coordinates: List<Pair<Double, Double>>): List<GeoPoint> {
        return coordinates.map { GeoPoint(it.first, it.second) }
    }

    /**
     * Calculate spherical distance between two coordinates in kilometers using the Haversine formula.
     */
    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (EARTH_RADIUS_KM * c).toFloat()
    }

    /**
     * Calculate spherical distance between two coordinates in meters.
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return calculateDistanceKm(lat1, lon1, lat2, lon2) * 1000.0
    }

    /**
     * Check if a coordinate is within a given radius (in km) from a center point.
     */
    fun isPointInRadius(
        pointLat: Double,
        pointLon: Double,
        centerLat: Double,
        centerLon: Double,
        radiusKm: Double
    ): Boolean {
        val distance = calculateDistanceKm(pointLat, pointLon, centerLat, centerLon)
        return distance <= radiusKm
    }

    /**
     * Launches external navigation via generic geo intent or browser map link.
     */
    fun openExternalMap(context: Context, latitude: Double, longitude: Double, label: String = "Location") {
        val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($label)")
        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(mapIntent)
        } catch (e: Exception) {
            val webUri = Uri.parse("https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=16/$latitude/$longitude")
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }

    /**
     * Projects a GeoPoint by a given distance in meters along a specified bearing in degrees.
     */
    fun movePointByDistance(startPoint: GeoPoint, distanceMeters: Double, bearingDegrees: Double): GeoPoint {
        val lat1 = Math.toRadians(startPoint.latitude)
        val lon1 = Math.toRadians(startPoint.longitude)
        val d = distanceMeters / (EARTH_RADIUS_KM * 1000.0) // Angular distance in radians
        val bearing = Math.toRadians(bearingDegrees)

        val lat2 = asin(
            sin(lat1) * cos(d) +
            cos(lat1) * sin(d) * cos(bearing)
        )

        val lon2 = lon1 + atan2(
            sin(bearing) * sin(d) * cos(lat1),
            cos(d) - sin(lat1) * sin(lat2)
        )

        return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    /**
     * Calculates the initial bearing (forward azimuth) from one GeoPoint to another in degrees [0, 360).
     */
    fun calculateBearing(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)

        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }
}
