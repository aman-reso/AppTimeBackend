package com.apptime.code.features

import com.apptime.code.common.respondApi
import com.apptime.code.common.respondError
import com.apptime.code.common.userId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

/**
 * Configure feature flags routes
 */
fun Application.configureFeatureFlagsRoutes() {
    val repository = FeatureFlagsRepository()
    val service = FeatureFlagsService(repository)
    
    routing {
        route("/api/features") {
            /**
             * GET /api/features
             * Get all feature flags (public endpoint for frontend)
             * Query params: country, appVersion, language (optional)
             * Headers: X-App-Language, X-App-Version (used if query params not provided)
             * Returns a simple map of featureName -> isEnabled (evaluated based on conditions)
             */
            get {
                try {
                    // Get parameters from query params or headers
                    val country = call.request.queryParameters["country"]
                        ?: call.request.headers["X-Country"]
                    val appVersion = call.request.queryParameters["appVersion"]
                        ?: call.request.headers["X-App-Version"]
                    val language = call.request.queryParameters["language"]
                        ?: call.request.headers["X-App-Language"]
                    val userId = call.userId // Get from auth if available
                    
                    val params = FeatureEvaluationParams(
                        country = country,
                        appVersion = appVersion,
                        language = language,
                        userId = userId
                    )
                    
                    val response = service.getFeatureFlagsMap(params)
                    call.respondApi(response, "Feature flags retrieved successfully")
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve feature flags: ${e.message}")
                }
            }
            
            /**
             * GET /api/features/{featureName}
             * Get a specific feature flag by name (public endpoint)
             * Query params: country, appVersion, language (optional)
             * Returns the evaluated isEnabled status based on conditions
             */
            get("/{featureName}") {
                try {
                    val featureName = call.parameters["featureName"]
                        ?: throw IllegalArgumentException("Feature name is required")
                    
                    // Get parameters from query params or headers
                    val country = call.request.queryParameters["country"]
                        ?: call.request.headers["X-Country"]
                    val appVersion = call.request.queryParameters["appVersion"]
                        ?: call.request.headers["X-App-Version"]
                    val language = call.request.queryParameters["language"]
                        ?: call.request.headers["X-App-Language"]
                    val userId = call.userId
                    
                    val params = FeatureEvaluationParams(
                        country = country,
                        appVersion = appVersion,
                        language = language,
                        userId = userId
                    )
                    
                    val feature = service.getFeatureFlagByName(featureName)
                    if (feature != null) {
                        // Evaluate the feature based on conditions
                        val isEnabled = ConditionEvaluator.evaluateFeature(
                            feature.isEnabled,
                            feature.conditions,
                            params
                        )
                        val evaluatedFeature = feature.copy(isEnabled = isEnabled)
                        call.respondApi(evaluatedFeature, "Feature flag retrieved successfully")
                    } else {
                        call.respondError(HttpStatusCode.NotFound, "Feature flag '$featureName' not found")
                    }
                } catch (e: IllegalArgumentException) {
                    call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve feature flag: ${e.message}")
                }
            }
        }
        
        // Admin routes for managing feature flags
        route("/api/admin/features") {
            /**
             * GET /api/admin/features
             * Get all feature flags with details (admin endpoint)
             */
            get {
                try {
                    val features = service.getAllFeatureFlags()
                    call.respondApi(features, "Feature flags retrieved successfully")
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve feature flags: ${e.message}")
                }
            }
            
            /**
             * GET /api/admin/features/{featureName}
             * Get a specific feature flag by name (admin endpoint)
             */
            get("/{featureName}") {
                try {
                    val featureName = call.parameters["featureName"]
                        ?: throw IllegalArgumentException("Feature name is required")
                    
                    val feature = service.getFeatureFlagByName(featureName)
                    if (feature != null) {
                        call.respondApi(feature, "Feature flag retrieved successfully")
                    } else {
                        call.respondError(HttpStatusCode.NotFound, "Feature flag '$featureName' not found")
                    }
                } catch (e: IllegalArgumentException) {
                    call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve feature flag: ${e.message}")
                }
            }
            
            /**
             * POST /api/admin/features
             * Create a new feature flag (admin endpoint)
             */
            post {
                try {
                    val request = call.receive<CreateFeatureFlagRequest>()
                    val feature = service.createFeatureFlag(request)
                    call.respondApi(feature, "Feature flag created successfully", HttpStatusCode.Created)
                } catch (e: IllegalArgumentException) {
                    call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.InternalServerError, "Failed to create feature flag: ${e.message}")
                }
            }
            
            /**
             * PUT /api/admin/features/{featureName}
             * Update a feature flag (admin endpoint)
             */
            put("/{featureName}") {
                try {
                    val featureName = call.parameters["featureName"]
                        ?: throw IllegalArgumentException("Feature name is required")
                    val request = call.receive<UpdateFeatureFlagRequest>()
                    val feature = service.updateFeatureFlag(featureName, request)
                    call.respondApi(feature, "Feature flag updated successfully")
                } catch (e: IllegalArgumentException) {
                    call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.InternalServerError, "Failed to update feature flag: ${e.message}")
                }
            }
            
            /**
             * DELETE /api/admin/features/{featureName}
             * Delete a feature flag (admin endpoint)
             */
            delete("/{featureName}") {
                try {
                    val featureName = call.parameters["featureName"]
                        ?: throw IllegalArgumentException("Feature name is required")
                    val deleted = service.deleteFeatureFlag(featureName)
                    if (deleted) {
                        call.respondApi("", "Feature flag deleted successfully")
                    } else {
                        call.respondError(HttpStatusCode.NotFound, "Feature flag '$featureName' not found")
                    }
                } catch (e: IllegalArgumentException) {
                    call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.InternalServerError, "Failed to delete feature flag: ${e.message}")
                }
            }
        }
    }
}

