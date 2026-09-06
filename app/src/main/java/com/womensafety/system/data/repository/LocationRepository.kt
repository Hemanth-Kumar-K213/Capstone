package com.womensafety.system.data.repository

import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.data.model.UserLocation
import kotlinx.coroutines.flow.Flow

/**
 * Repository for saving, retrieving, and listening to user location points.
 * All Firebase interactions are routed strictly through FirestoreManager.
 */
class LocationRepository(
    private val firestoreManager: FirestoreManager = FirestoreManager()
) {

    suspend fun saveLocation(userId: String, location: UserLocation): Result<Unit> {
        return firestoreManager.saveUserLocation(userId, location)
    }

    suspend fun getLatestLocation(userId: String): Result<UserLocation?> {
        return firestoreManager.getLatestUserLocation(userId)
    }

    suspend fun getLocationHistory(userId: String, limit: Int = 50): Result<List<UserLocation>> {
        return firestoreManager.getUserLocationHistory(userId, limit)
    }

    fun observeUserLocation(userId: String): Flow<UserLocation?> {
        return firestoreManager.listenToUserLocation(userId)
    }

    suspend fun deleteOldLocations(userId: String, beforeTimestamp: Long): Result<Unit> {
        return firestoreManager.deleteOldLocations(userId, beforeTimestamp)
    }
}
