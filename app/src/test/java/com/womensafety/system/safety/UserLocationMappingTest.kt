package com.womensafety.system.safety

import com.womensafety.system.data.model.UserLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserLocationMappingTest {

    @Test
    fun testUserLocation_fromMap_standardFields() {
        val map = mapOf(
            "userId" to "user123",
            "uid" to "TRN-987654",
            "userName" to "Alice",
            "latitude" to 12.9716,
            "longitude" to 77.5946,
            "accuracy" to 10f,
            "address" to "MG Road, Bengaluru"
        )
        val loc = UserLocation.fromMap("doc1", map)

        assertEquals("doc1", loc.id)
        assertEquals("user123", loc.userId)
        assertEquals("TRN-987654", loc.uid)
        assertEquals("Alice", loc.userName)
        assertEquals(12.9716, loc.latitude, 0.0001)
        assertEquals(77.5946, loc.longitude, 0.0001)
        assertEquals(10f, loc.accuracy, 0.1f)
        assertEquals("MG Road, Bengaluru", loc.address)
    }

    @Test
    fun testUserLocation_fromMap_childLocationShortFieldsAndDocumentIdUid() {
        val map = mapOf(
            "userId" to "user456",
            "childName" to "Bob",
            "lat" to 13.0827,
            "lng" to 80.2707,
            "accuracy" to 15f
        )
        val loc = UserLocation.fromMap("TRN-112233", map)

        assertEquals("user456", loc.userId)
        assertEquals("TRN-112233", loc.uid) // Uses document ID as UID fallback
        assertEquals("Bob", loc.userName) // Uses childName as userName fallback
        assertEquals(13.0827, loc.latitude, 0.0001) // Uses lat fallback
        assertEquals(80.2707, loc.longitude, 0.0001) // Uses lng fallback
    }

    @Test
    fun testUserLocation_fromMap_weeklyUidFieldFallback() {
        val map = mapOf(
            "userId" to "user789",
            "weeklyUid" to "TRN-445566",
            "name" to "Carol",
            "lat" to 28.6139,
            "lng" to 77.2090
        )
        val loc = UserLocation.fromMap("doc_xyz", map)

        assertEquals("user789", loc.userId)
        assertEquals("TRN-445566", loc.uid) // Uses weeklyUid field
        assertEquals("Carol", loc.userName) // Uses name field
        assertEquals(28.6139, loc.latitude, 0.0001)
        assertEquals(77.2090, loc.longitude, 0.0001)
    }
}
