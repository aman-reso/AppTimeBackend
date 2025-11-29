package com.apptime.code.admin

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
    val createdAt: String,
    val lastSyncTime: String? = null
)

@Serializable
data class UpdateUserRequest(
    val username: String? = null,
    val email: String? = null,
    val name: String? = null,
    val totpEnabled: Boolean? = null
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

