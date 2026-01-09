package com.apptime.code.common

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.Attributes
import io.ktor.util.AttributeKey
import org.slf4j.LoggerFactory

/**
 * Configuration for tracking app version and language headers across all APIs
 * This helps track which app version or language caused any issues or bugs
 */
fun Application.configureHeaderTracking() {
    val logger = LoggerFactory.getLogger("AppHeaderTracking")
    
    intercept(ApplicationCallPipeline.Call) {
        val appLanguage = call.request.headers["X-App-Language"]
        val appVersion = call.request.headers["X-App-Version"]
        val path = call.request.path()
        
        // Log headers for tracking (only for API routes, skip static resources)
        if (path.startsWith("/api") || path.startsWith("/health") || path == "/") {
            logger.info(
                "API Request - Path: $path | " +
                "X-App-Language: ${appLanguage ?: "not provided"} | " +
                "X-App-Version: ${appVersion ?: "not provided"}"
            )
        }
        
        // Store headers in call attributes for easy access throughout the request lifecycle
        appLanguage?.let { call.attributes.put(HeaderAttributes.AppLanguage, it) }
        appVersion?.let { call.attributes.put(HeaderAttributes.AppVersion, it) }
        
        proceed()
    }
}

/**
 * Attributes to store header values in the call context
 */
object HeaderAttributes {
    val AppLanguage = AttributeKey<String>("AppLanguage")
    val AppVersion = AttributeKey<String>("AppVersion")
}

/**
 * Extension to get app language from call attributes
 */
val ApplicationCall.appLanguageFromAttributes: String?
    get() = attributes.getOrNull(HeaderAttributes.AppLanguage)

/**
 * Extension to get app version from call attributes
 */
val ApplicationCall.appVersionFromAttributes: String?
    get() = attributes.getOrNull(HeaderAttributes.AppVersion)

