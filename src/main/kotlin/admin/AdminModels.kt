package com.apptime.code.admin

import com.apptime.code.challenges.AppDetail
import kotlinx.serialization.Serializable

// Challenge Management Models
@Serializable
data class CreateChallengeRequest(
    val title: String,
    val description: String? = null,
    val reward: String? = null,
    val prize: String? = null,
    val rules: String? = null,
    val startTime: String, // ISO 8601
    val endTime: String,   // ISO 8601
    val thumbnail: String? = null,
    val challengeType: String, // "LESS_SCREENTIME" or "MORE_SCREENTIME"
    val packageNames: String? = null,
    val displayType: String? = null,
    val tags: String? = null, // Comma-separated
    val sponsor: String? = null,
    val colorScheme: String? = null, // Color scheme for the challenge (e.g., "blue", "purple", "green")
    val variant: String? = null, // Challenge varient (e.g., "varient1", "varient2", "varient3")
    val appdetail: List<AppDetail>? = null, // List of app details with appname and url
    val isActive: Boolean = true
)

@Serializable
data class UpdateChallengeRequest(
    val title: String? = null,
    val description: String? = null,
    val reward: String? = null,
    val prize: String? = null,
    val rules: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val thumbnail: String? = null,
    val challengeType: String? = null,
    val packageNames: String? = null,
    val displayType: String? = null,
    val tags: String? = null,
    val sponsor: String? = null,
    val colorScheme: String? = null, // Color scheme for the challenge (e.g., "blue", "purple", "green")
    val variant: String? = null, // Challenge varient (e.g., "varient1", "varient2", "varient3")
    val appdetail: List<AppDetail>? = null, // List of app details with appname and url
    val isActive: Boolean? = null
)

@Serializable
data class AdminChallengeResponse(
    val id: Long,
    val title: String,
    val description: String? = null,
    val reward: String? = null,
    val prize: String? = null,
    val rules: String? = null,
    val startTime: String,
    val endTime: String,
    val thumbnail: String? = null,
    val challengeType: String,
    val packageNames: String? = null,
    val displayType: String? = null,
    val tags: String? = null,
    val sponsor: String? = null,
    val colorScheme: String? = null, // Color scheme for the challenge (e.g., "blue", "purple", "green")
    val variant: String? = null, // Challenge varient (e.g., "varient1", "varient2", "varient3")
    val appdetail: List<AppDetail>? = null, // List of app details with appname and url
    val isActive: Boolean,
    val participantCount: Long,
    val createdAt: String
)

// User Management Models
@Serializable
data class AdminUserResponse(
    val userId: String,
    val username: String? = null,
    val email: String? = null,
    val name: String? = null,
    val deviceId: String,
    val deviceModel: String? = null,
    val manufacturer: String? = null,
    val androidVersion: String? = null,
    val totpEnabled: Boolean,
    val isBlocked: Boolean,
    val createdAt: String,
    val lastSyncTime: String? = null
)

@Serializable
data class UpdateUserRequest(
    val username: String? = null,
    val email: String? = null,
    val name: String? = null,
    val totpEnabled: Boolean? = null,
    val isBlocked: Boolean? = null
)

// Consent Template Management Models
@Serializable
data class CreateConsentTemplateRequest(
    val name: String,
    val description: String? = null,
    val isMandatory: Boolean = false
)

@Serializable
data class UpdateConsentTemplateRequest(
    val name: String? = null,
    val description: String? = null,
    val isMandatory: Boolean? = null
)

@Serializable
data class AdminConsentTemplateResponse(
    val id: Int,
    val name: String,
    val description: String? = null,
    val isMandatory: Boolean,
    val userCount: Long // Number of users who have submitted this consent
)

// Admin Authentication Models
@Serializable
data class AdminLoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class AdminLoginResponse(
    val token: String
)

@Serializable
data class AdminVerifyRequest(
    val token: String
)

@Serializable
data class AdminVerifyResponse(
    val valid: Boolean
)

// Reward Management Models
@Serializable
data class CreateRewardRequest(
    val userId: String,
    val type: String, // POINTS, BADGE, COUPON, TROPHY, CUSTOM
    val source: String, // CHALLENGE_WIN, CHALLENGE_PARTICIPATION, DAILY_LOGIN, etc.
    val title: String,
    val description: String? = null,
    val amount: Long = 0,
    val metadata: String? = null,
    val challengeId: Long? = null,
    val challengeTitle: String? = null,
    val rank: Int? = null
)

@Serializable
data class UpdateRewardRequest(
    val userId: String? = null,
    val type: String? = null,
    val source: String? = null,
    val title: String? = null,
    val description: String? = null,
    val amount: Long? = null,
    val metadata: String? = null,
    val challengeId: Long? = null,
    val challengeTitle: String? = null,
    val rank: Int? = null,
    val isClaimed: Boolean? = null
)

@Serializable
data class AdminRewardResponse(
    val id: Long,
    val userId: String,
    val type: String,
    val source: String,
    val title: String,
    val description: String? = null,
    val amount: Long = 0,
    val metadata: String? = null,
    val challengeId: Long? = null,
    val challengeTitle: String? = null,
    val rank: Int? = null,
    val earnedAt: String,
    val isClaimed: Boolean = false,
    val claimedAt: String? = null
)

