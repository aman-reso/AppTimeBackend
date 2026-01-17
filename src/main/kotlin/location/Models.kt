package com.apptime.code.location

import kotlinx.serialization.Serializable

/**
 * Request model for syncing location from Android
 */
@Serializable
data class LocationSyncRequest(
    val ip: String? = null, // Optional IP address
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null, // Address derived from latlong
    val lastSyncTime: String? = null // ISO 8601 timestamp (e.g., "2024-01-15T10:00:00Z")
)

/**
 * LatLong model for latitude and longitude
 */
@Serializable
data class LatLong(
    val latitude: Double,
    val longitude: Double
)

/**
 * Location data model for response
 */
@Serializable
data class LocationData(
    val id: Long,
    val userId: String,
    val latitude: Double?,
    val longitude: Double?,
    val address: String?,
    val ipAddress: String?,
    val timestamp: String, // ISO 8601 timestamp (lastSyncTime)
    val lastSyncTime: String? = null, // Can be null
    val createdAt: String // ISO 8601 timestamp
)

/**
 * Response wrapper for location data
 */
@Serializable
data class LocationDataWrapper(
    val location: LocationData? = null, // Can be null if location data not available
    val message: String
)

/**
 * Response model for location sync
 */
@Serializable
data class LocationSyncResponse(
    val success: Boolean,
    val message: String,
    val lastSyncTime: String? = null // ISO 8601 timestamp
)

