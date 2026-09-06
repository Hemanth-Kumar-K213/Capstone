package com.womensafety.system.data.model

/**
 * A manually-added emergency contact (e.g., parent, sibling).
 * Stored as a list inside the User's profile document.
 * These contacts don't need the app installed — just name + phone.
 */
data class EmergencyContact(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val phone: String = "",
    val addedAt: Long = System.currentTimeMillis()
) {
    constructor() : this(id = java.util.UUID.randomUUID().toString())

    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "phone" to phone,
        "addedAt" to addedAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): EmergencyContact {
            return EmergencyContact(
                id = map["id"] as? String ?: java.util.UUID.randomUUID().toString(),
                name = map["name"] as? String ?: "",
                phone = map["phone"] as? String ?: "",
                addedAt = (map["addedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}
