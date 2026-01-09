package com.apptime.code.features

/**
 * Service layer for feature flags
 */
class FeatureFlagsService(private val repository: FeatureFlagsRepository) {
    
    /**
     * Get all feature flags as a map (for frontend)
     * Evaluates conditions based on provided parameters
     * Returns a simple map of featureName -> isEnabled
     */
    fun getFeatureFlagsMap(params: FeatureEvaluationParams = FeatureEvaluationParams()): FeatureFlagsResponse {
        val flags = repository.getAllFeatureFlags()
        val featuresMap = flags.associate { flag ->
            val isEnabled = ConditionEvaluator.evaluateFeature(
                flag.isEnabled,
                flag.conditions,
                params
            )
            flag.featureName to isEnabled
        }
        return FeatureFlagsResponse(features = featuresMap)
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

