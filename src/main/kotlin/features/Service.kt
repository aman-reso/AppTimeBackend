package com.apptime.code.features

import com.apptime.code.common.EnvLoader

/**
 * Service layer for feature flags
 */
class FeatureFlagsService(private val repository: FeatureFlagsRepository) {
    
    /**
     * Get all feature flags (for frontend)
     * Evaluates conditions based on provided parameters
     * Returns only enabled features as a list
     */
    fun getFeatureFlagsMap(params: FeatureEvaluationParams = FeatureEvaluationParams()): FeatureFlagsResponse {
        val flags = repository.getAllFeatureFlags()
        val enabledFeatures = flags
            .filter { flag ->
                ConditionEvaluator.evaluateFeature(
                    flag.isEnabled,
                    flag.conditions,
                    params
                )
            }
            .map { it.featureName }
            .sorted() // Sort alphabetically for consistent ordering
        
        // Read configuration values from environment variables
        val challengeBannerURL = EnvLoader.getEnv("CHALLENGE_BANNER_URL")
        val wallpaperBannerURL = EnvLoader.getEnv("WALLPAPER_BANNER_URL")
        val adWeightage = EnvLoader.getEnv("AD_WEIGHTAGE")?.toIntOrNull()
        val wallpaperBaseURL = EnvLoader.getEnv("WALLPAPER_BASE_URL")
        
        return FeatureFlagsResponse(
            features = FeatureFlagsInner(
                enabled = enabledFeatures,
                challangeBannerURL = challengeBannerURL,
                wallpaperBannerURL = wallpaperBannerURL,
                adWeightage = adWeightage,
                wallpaperBaseURL = wallpaperBaseURL
            )
        )
    }
    
    /**
     * Get all feature flags with details (for admin)
     */
    fun getAllFeatureFlags(): List<FeatureFlag> {
        return repository.getAllFeatureFlags()
    }
    
    /**
     * Get a specific feature flag by name
     */
    fun getFeatureFlagByName(featureName: String): FeatureFlag? {
        return repository.getFeatureFlagByName(featureName)
    }
    
    /**
     * Create a new feature flag
     */
    fun createFeatureFlag(request: CreateFeatureFlagRequest): FeatureFlag {
        // Validate feature name
        if (request.featureName.isBlank()) {
            throw IllegalArgumentException("Feature name cannot be blank")
        }
        
        // Check if feature already exists
        if (repository.featureFlagExists(request.featureName)) {
            throw IllegalArgumentException("Feature flag '${request.featureName}' already exists")
        }
        
        val id = repository.createFeatureFlag(request)
        return repository.getFeatureFlagById(id)
            ?: throw IllegalStateException("Failed to create feature flag")
    }
    
    /**
     * Update a feature flag
     */
    fun updateFeatureFlag(featureName: String, request: UpdateFeatureFlagRequest): FeatureFlag {
        val updated = repository.updateFeatureFlag(featureName, request)
        if (!updated) {
            throw IllegalArgumentException("Feature flag '$featureName' not found")
        }
        
        return repository.getFeatureFlagByName(featureName)
            ?: throw IllegalStateException("Failed to retrieve updated feature flag")
    }
    
    /**
     * Update a feature flag by ID
     */
    fun updateFeatureFlagById(id: Int, request: UpdateFeatureFlagRequest): FeatureFlag {
        val updated = repository.updateFeatureFlagById(id, request)
        if (!updated) {
            throw IllegalArgumentException("Feature flag with ID $id not found")
        }
        
        return repository.getFeatureFlagById(id)
            ?: throw IllegalStateException("Failed to retrieve updated feature flag")
    }
    
    /**
     * Delete a feature flag
     */
    fun deleteFeatureFlag(featureName: String): Boolean {
        return repository.deleteFeatureFlag(featureName)
    }
}

