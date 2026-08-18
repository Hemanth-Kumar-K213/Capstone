package com.womensafety.system.data.model

/**
 * A manually-added emergency contact (e.g., parent, sibling).
 * Stored as a list inside the User's profile document.
 * These contacts don't need the app installed — just name + phone.
 */
data class EmergencyContact(
    val name: String = "",
    val phone: String = ""
) {
    constructor() : this(name = "")

    fun toMap(): Map<String, Any> = mapOf(
        "name" to name,
        "phone" to phone
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): EmergencyContact {
            return EmergencyContact(
                name = map["name"] as? String ?: "",
                phone = map["phone"] as? String ?: ""
            )
        }
    }
}
