package com.apptime.code.features

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Feature flag conditions
 */
@Serializable
data class FeatureConditions(
    val countries: List<String>? = null, // ISO country codes: ["US", "IN", "GB"]
    val appVersions: List<String>? = null, // Version constraints: [">=1.0.0", "<2.0.0"]
    val languages: List<String>? = null, // Language codes: ["en", "es", "fr"]
    val userIds: List<String>? = null, // Specific user IDs (for beta testing)
    val percentage: Int? = null // Percentage rollout (0-100)
)

/**
 * Feature flag model
 */
@Serializable
data class FeatureFlag(
    val id: Int,
    val featureName: String,
    val isEnabled: Boolean,
    val description: String? = null,
    val conditions: FeatureConditions? = null,
    val createdAt: String,
    val updatedAt: String
)

/**
 * Feature flags inner data structure
 */
@Serializable
data class FeatureFlagsInner(
    val enabled: List<String>, // List of enabled feature names
    val challangeBannerURL: String? = null, // Challenge banner URL (note: keeping user's spelling)
    val wallpaperBannerURL: String? = null, // Wallpaper banner URL
    val adWeightage: Int? = null, // Ad weightage
    val wallpaperBaseURL: String? = null // Wallpaper base URL
)

/**
 * Feature flags response (for frontend)
 */
@Serializable
data class FeatureFlagsResponse(
    val features: FeatureFlagsInner
)

/**
 * Request to update a feature flag (admin)
 */
@Serializable
data class UpdateFeatureFlagRequest(
    val isEnabled: Boolean,
    val description: String? = null,
    val conditions: FeatureConditions? = null
)

/**
 * Request to create a feature flag (admin)
 */
@Serializable
data class CreateFeatureFlagRequest(
    val featureName: String,
    val isEnabled: Boolean = false,
    val description: String? = null,
    val conditions: FeatureConditions? = null
)

/**
 * Parameters for evaluating feature flags (from request headers/query params)
 */
data class FeatureEvaluationParams(
    val country: String? = null,
    val appVersion: String? = null,
    val language: String? = null,
    val userId: String? = null
)

/**
 * Feature configuration model - stores global feature settings
 */
@Serializable
data class FeatureConfiguration(
    val challengeBannerURL: String? = null,
    val wallpaperBannerURL: String? = null,
    val adWeightage: Int? = null,
    val wallpaperBaseURL: String? = null,
    val updatedAt: String? = null
)

/**
 * Request to update feature configuration
 */
@Serializable
data class UpdateFeatureConfigurationRequest(
    val challengeBannerURL: String? = null,
    val wallpaperBannerURL: String? = null,
    val adWeightage: Int? = null,
    val wallpaperBaseURL: String? = null
)

