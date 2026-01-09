package com.apptime.code

import DatabaseFactory
import com.apptime.code.admin.configureAdminRoutes
import com.apptime.code.challenges.configureChallengeRoutes
import com.apptime.code.common.configureAuthentication
import com.apptime.code.common.configureHeaderTracking
import com.apptime.code.consents.configureConsentRoutes
import com.apptime.code.features.configureFeatureFlagsRoutes
import com.apptime.code.focus.configureFocusRoutes
import com.apptime.code.leaderboard.configureLeaderboardRoutes
import com.apptime.code.rewards.configureRewardRoutes
import users.configureUserRoutes
import usage.configureAppUsageEventRoutes
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import kotlinx.serialization.json.Json

fun Application.module() {
    // Initialize database
    DatabaseFactory.init()

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        })
    }

    // Configure header tracking for all APIs (X-App-Language and X-App-Version)
    configureHeaderTracking()

    configureAuthentication()
    configureUserRoutes()
    configureConsentRoutes()
    configureFocusRoutes()
    configureAppUsageEventRoutes()
    configureLeaderboardRoutes()
    configureChallengeRoutes()
    configureRewardRoutes()
    configureFeatureFlagsRoutes()
    configureAdminRoutes()
    
    // Configure scheduled jobs (cronjobs)
    configureScheduledJobs()

    routing {
        // Serve admin dashboard
        staticResources("/admin", "admin") {
            default("index.html")
        }
        
        get("/") {
            call.respond(
                mapOf(
                    "message" to "Ktor is working! 🚀",
                    "status" to "success",
                    "database" to "connected"
                )
            )
        }

        get("/health") {
            call.respond(
                mapOf(
                    "status" to "healthy",
                    "service" to "AppTimeBackend",
                    "database" to "connected"
                )
            )
        }
        
        // Android App Links verification
        route(".well-known") {
            get("/assetlinks.json") {
                // Set content type to application/json
                call.response.headers.append("Content-Type", "application/json")
                
                // Android App Links assetlinks.json
                val assetLinks = listOf(
                    mapOf(
                        "relation" to listOf(
                            "delegate_permission/common.handle_all_urls",
                            "delegate_permission/common.get_login_creds"
                        ),
                        "target" to mapOf(
                            "namespace" to "android_app",
                            "package_name" to "com.app.screentime",
                            "sha256_cert_fingerprints" to listOf(
                                "61:B2:93:19:28:69:7D:4B:51:24:AB:D0:83:A9:2C:3A:2E:BA:D6:0E:C7:30:95:70:E1:F9:0C:1C:2E:44:E6:E0"
                                // Add additional fingerprints here if needed (e.g., for debug/release variants)
                            )
                        )
                    )
                )
                
                call.respond(assetLinks)
            }
        }
    }
}
