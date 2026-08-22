package com.womensafety.system.data.model

/**
 * Represents the lifecycle state of the LocationTrackingService.
 */
enum class LocationServiceState {
    IDLE,           // Service not started
    STARTING,       // Service initializing
    TRACKING,       // Actively tracking location
    PAUSED,         // Temporarily paused
    STOPPING,       // Service shutting down
    ERROR           // Error state
}
