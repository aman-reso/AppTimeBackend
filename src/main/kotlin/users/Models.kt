package users

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    val deviceId: String,
    val manufacturer: String? = null,
    val model: String? = null,
    val brand: String? = null,
    val product: String? = null,
    val device: String? = null,
    val hardware: String? = null,
    val androidVersion: String? = null,
    val sdkVersion: Int? = null
)

@Serializable
data class DeviceRegistrationRequest(
    val deviceInfo: DeviceInfo,
    val firebaseToken: String? = null // Optional Firebase Cloud Messaging (FCM) token
)

@Serializable
data class DeviceRegistrationResponse(
    val userId: String,
    val username: String? = null,
    val createdAt: String,
    val totpSecret: String? = null,
    val totpEnabled: Boolean = true
)

@Serializable
data class UserProfile(
    val userId: String,
    val username: String? = null,
    val email: String? = null,
    val name: String? = null,
    val country: String? = null, // User's country (ISO country code or name)
    val firebaseToken: String? = null, // Firebase Cloud Messaging (FCM) token
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val lastSyncTime: String? = null // ISO 8601 format - syncTime from usage events batch request
)

@Serializable
data class UserSettings(
    val userId: String,
    val dailyLimitReached: Boolean,
    val breakReminders: Boolean,
    val weeklyReports: Boolean,
    val appBlocking: Boolean,
    val quietHours: QuietHours? = null
)

@Serializable
data class QuietHours(
    val enabled: Boolean,
    val startTime: String, // HH:MM format
    val endTime: String,   // HH:MM format
    val days: List<String> // ["monday", "tuesday", etc.]
)

@Serializable
data class UserSearchResult(
    val username: String? = null, // Only username, never userId
    val email: String? = null,
    val name: String? = null,
    val createdAt: String? = null,
    val isActive: Boolean? = null
)

@Serializable
data class PublicUserProfile(
    val username: String? = null,
    val email: String? = null,
    val name: String? = null,
    val country: String? = null, // User's country (ISO country code or name)
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val lastSyncTime: String? = null // ISO 8601 format - syncTime from usage events batch request
    // userId is NEVER included in public profile
)

@Serializable
data class SimpleResponse(
    val success: Boolean,
    val message: String? = null
)

@Serializable
data class ChangeUsernameRequest(
    val username: String
)

