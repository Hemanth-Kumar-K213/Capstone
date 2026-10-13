package com.womensafety.system.safety

import com.womensafety.system.data.firebase.FirestoreManager
import com.womensafety.system.data.model.PermissionState
import com.womensafety.system.data.model.Relationship
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelationshipDeletionTest {

    @Test
    fun testRelationshipDocId_format() {
        val userId = "user_alpha"
        val tcId = "tc_bravo"
        val docId = FirestoreManager.relationshipDocId(userId, tcId)
        assertEquals("user_alpha_tc_bravo", docId)

        val parts = docId.split("_", limit = 2)
        assertEquals("user", parts[0]) // Notice that split with limit 2 splits on first underscore
    }

    @Test
    fun testPermissionState_revokedAndDeniedStates() {
        val revokedRel = Relationship.fromMap(
            "rel1",
            mapOf("userId" to "u1", "trustedContactId" to "tc1", "status" to "REVOKED")
        )
        assertEquals(PermissionState.REVOKED, revokedRel.permissionState)

        val deniedRel = Relationship.fromMap(
            "rel2",
            mapOf("userId" to "u2", "trustedContactId" to "tc2", "status" to "DENIED")
        )
        assertEquals(PermissionState.DENIED, deniedRel.permissionState)
    }

    @Test
    fun testRelationshipFiltering_excludesRevokedAndDenied() {
        val relationships = listOf(
            Relationship(id = "r1", userId = "u1", status = PermissionState.APPROVED.name),
            Relationship(id = "r2", userId = "u2", status = PermissionState.PENDING.name),
            Relationship(id = "r3", userId = "u3", status = PermissionState.REVOKED.name),
            Relationship(id = "r4", userId = "u4", status = PermissionState.DENIED.name)
        )

        val activeList = relationships.filter {
            it.permissionState == PermissionState.APPROVED || it.permissionState == PermissionState.PENDING
        }

        assertEquals(2, activeList.size)
        assertTrue(activeList.any { it.id == "r1" })
        assertTrue(activeList.any { it.id == "r2" })
        assertFalse(activeList.any { it.id == "r3" })
        assertFalse(activeList.any { it.id == "r4" })
    }

    @Test
    fun testDeletionBlacklist_preventsStaleFirestoreReappearance() {
        val deletedRelIds = mutableSetOf("r1")
        val deletedUserIds = mutableSetOf("u2")

        val relationships = listOf(
            Relationship(id = "r1", userId = "u1", status = PermissionState.APPROVED.name),
            Relationship(id = "r2", userId = "u2", status = PermissionState.APPROVED.name),
            Relationship(id = "r3", userId = "u3", status = PermissionState.APPROVED.name)
        )

        val filtered = relationships.filter {
            !deletedRelIds.contains(it.id) &&
            !deletedUserIds.contains(it.userId) &&
            (it.permissionState == PermissionState.APPROVED || it.permissionState == PermissionState.PENDING)
        }

        assertEquals(1, filtered.size)
        assertEquals("r3", filtered[0].id)
        assertEquals("u3", filtered[0].userId)
    }
}
