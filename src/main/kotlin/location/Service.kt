package com.apptime.code.location

import users.UserRepository
import users.UserService
import kotlinx.datetime.Instant

/**
 * Location service layer - handles business logic
 */
class LocationService(
    private val repository: LocationRepository,
    private val userRepository: UserRepository,
    private val userService: UserService
) {

    /**
     * Sync location data for authenticated user
     */
    suspend fun syncLocation(userId: String, request: LocationSyncRequest): LocationSyncResponse {
        if (userId.isBlank()) {
            throw IllegalArgumentException("User ID is required")
        }

        // Validate user exists
        if (!userRepository.userExists(userId)) {
            throw IllegalArgumentException("User not found")
        }

        // Convert lastSyncTime from ISO 8601 string to Instant
        val syncTime = if (request.lastSyncTime != null && request.lastSyncTime.isNotBlank()) {
            try {
                Instant.parse(request.lastSyncTime)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid lastSyncTime format. Expected ISO 8601 format (e.g., '2024-01-15T10:00:00Z')")
            }
        } else {
            kotlinx.datetime.Clock.System.now()
        }

        // Upsert location data
        repository.upsertLocation(
            userId = userId,
            ipAddress = request.ip,
            latitude = request.latitude,
            longitude = request.longitude,
            address = request.address,
            lastSyncTime = syncTime
        )

        return LocationSyncResponse(
            success = true,
            message = "Location synced successfully",
            lastSyncTime = syncTime.toString()
        )
    }

    /**
     * Get location data for a user by username
     * Only accessible if requestingUserId has verified target user via TOTP
     */
    suspend fun getLocationByUsername(
        username: String,
        requestingUserId: String
    ): LocationDataWrapper {
        if (username.isBlank()) {
            throw IllegalArgumentException("Username is required")
        }

        if (requestingUserId.isBlank()) {
            throw IllegalArgumentException("Requesting user ID is required")
        }

        // Get target user ID by username
        val targetUserId = userRepository.getUserIdByUsername(username)
            ?: throw IllegalArgumentException("User not found")

        // Check if requesting user has valid TOTP verification session for target user
        if (!userService.hasAccessToUserData(requestingUserId, targetUserId)) {
            throw SecurityException("Access denied. Please verify TOTP code first.")
        }

        // Get location data
        val location = repository.getLocationByUserId(targetUserId)

        // If location data not found, return null location instead of throwing error
        return if (location != null) {
            // Convert to response model
            LocationDataWrapper(
                location = LocationData(
                    id = location.id,
                    userId = location.userId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    address = location.address,
                    ipAddress = location.ipAddress,
                    timestamp = location.lastSyncTime.toString(), // Use lastSyncTime as timestamp
                    lastSyncTime = null, // Can be null as per example
                    createdAt = location.createdAt.toString()
                ),
                message = "Last location retrieved successfully"
            )
        } else {
            // Return null location data
            LocationDataWrapper(
                location = null,
                message = "Location data not available for this user"
            )
        }
    }
}
