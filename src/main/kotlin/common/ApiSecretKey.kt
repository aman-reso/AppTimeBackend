package com.apptime.code.common

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

/**
 * Configuration for API secret key validation
 * Validates that all API requests from frontend include a valid secret key in headers
 */
fun Application.configureApiSecretKey() {
    val logger = LoggerFactory.getLogger("ApiSecretKeyValidation")
    
    // Get secret key from environment variable
    val apiSecretKey = EnvLoader.getEnv("API_SECRET_KEY")
    
    if (apiSecretKey.isNullOrBlank()) {
        logger.warn("⚠️  API_SECRET_KEY not set in environment variables. API secret key validation is DISABLED.")
        return
    }
    
    logger.info("✅ API secret key validation enabled")
    
    intercept(ApplicationCallPipeline.Call) {
        val path = call.request.path()
        
        // Skip validation for excluded paths
        val excludedPaths = listOf(
            "/health",
            "/",
            "/admin",  // Admin routes have their own authentication
            ".well-known"
        )
        
        val isExcluded = excludedPaths.any { path.startsWith(it) }
        
        // Only validate API routes (paths starting with /api)
        // Skip admin routes as they have their own authentication
        if (isExcluded && path.startsWith("/api") && !path.startsWith("/api/admin")) {
            // Check app version - if below 8, skip secret key validation
            val appVersion = call.request.headers["X-App-Version"]
            val requiresSecretKey = if (appVersion != null) {
                try {
                    // Parse version number (e.g., "8", "8.0", "8.1.2" -> extract major version)
                    val majorVersion = appVersion.split(".")[0].toIntOrNull() ?: 0
                    majorVersion >= 8
                } catch (e: Exception) {
                    // If version parsing fails, require secret key for safety
                    true
                }
            } else {
                // If version header is missing, require secret key for safety
                true
            }
            
            // Skip validation for app versions below 8
            if (!requiresSecretKey) {
                logger.debug("Skipping secret key validation for app version $appVersion (< 8). Path: $path")
            } else {
                // Validate secret key for app versions 8 and above
                val providedSecretKey = call.request.headers["X-API-Secret"] 
                    ?: call.request.headers["X-Secret-Key"]
                    ?: call.request.headers["API-Secret"]
                
                if (providedSecretKey.isNullOrBlank()) {
                    logger.warn("API request rejected - Missing secret key header. Path: $path, App Version: $appVersion")
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf(
                            "success" to false,
                            "status" to 401,
                            "message" to "API secret key is required. Please include X-API-Secret header.",
                            "error" to mapOf(
                                "code" to "MISSING_API_SECRET",
                                "message" to "Missing X-API-Secret header"
                            )
                        )
                    )
                    return@intercept
                }
                
                if (providedSecretKey != apiSecretKey) {
                    logger.warn("API request rejected - Invalid secret key. Path: $path, App Version: $appVersion")
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf(
                            "success" to false,
                            "status" to 401,
                            "message" to "Invalid API secret key.",
                            "error" to mapOf(
                                "code" to "INVALID_API_SECRET",
                                "message" to "The provided API secret key is invalid"
                            )
                        )
                    )
                    return@intercept
                }
                
                logger.debug("API secret key validated successfully. Path: $path, App Version: $appVersion")
            }
        }
        
        proceed()
    }
}

