package com.womensafety.system.data.model

/**
 * Requirement 4A: Device Battery Status Model.
 * Represents hardware battery metrics collected periodically for remote dashboard telemetry.
 */
data class BatteryStatus(
    val userId: String = "",
    val deviceId: String = "",
    val batteryPercentage: Int = 0, // 0-100
    val isCharging: Boolean = false,
    val chargingStatus: Int = 0, // 0: unknown, 1: AC, 2: USB, 4: Wireless
    val temperature: Int = 0, // Celsius
    val health: Int = 0, // BatteryManager.BATTERY_HEALTH_*
    val voltage: Int = 0, // mV
    val technology: String = "Li-ion",
    val timestamp: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "deviceId" to deviceId,
        "batteryPercentage" to batteryPercentage,
        "isCharging" to isCharging,
        "chargingStatus" to chargingStatus,
        "temperature" to temperature,
        "health" to health,
        "voltage" to voltage,
        "technology" to technology,
        "timestamp" to timestamp,
        "lastUpdated" to lastUpdated
    )
}
