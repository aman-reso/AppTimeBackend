package users

import kotlinx.serialization.Serializable

@Serializable
data class TOTPGenerateResponse(
    val code: String,
    val remainingSeconds: Int,
    val expiresAt: String // ISO 8601 timestamp
)

@Serializable
data class TOTPVerifyRequest(
    val code: String,
    val requestingUserId: String? = null, // User A's ID (who is requesting access)
    val durationSeconds: Int? = null // Optional: Duration in seconds for access (default: 3600 = 1 hour, min: 60, max: 86400 = 24 hours)
)

@Serializable
data class TOTPVerifyResponse(
    val valid: Boolean,
    val message: String,
    val validitySeconds: Int = 60, // TOTP codes are valid for 60 seconds
    val remainingSeconds: Int? = null, // Remaining seconds until code expires (if valid)
    val expiresAt: String? = null // ISO 8601 timestamp when code expires (if valid)
)

@Serializable
data class TOTPAccessStatusResponse(
    val hasAccess: Boolean,
    val message: String,
    val verifiedAt: String? = null, // ISO 8601 timestamp when TOTP was verified
    val expiresAt: String? = null, // ISO 8601 timestamp when access expires
    val remainingSeconds: Int? = null, // Remaining seconds until access expires
    val targetUsername: String? = null // Username of the target user
)

