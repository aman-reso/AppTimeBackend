package com.apptime.code

import DatabaseFactory
import com.apptime.code.admin.configureAdminRoutes
import com.apptime.code.challenges.configureChallengeRoutes
import com.apptime.code.common.configureAuthentication
import com.apptime.code.consents.configureConsentRoutes
import com.apptime.code.focus.configureFocusRoutes
import com.apptime.code.leaderboard.configureLeaderboardRoutes
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

    configureAuthentication()
    configureUserRoutes()
    configureConsentRoutes()
    configureFocusRoutes()
    configureAppUsageEventRoutes()
    configureLeaderboardRoutes()
    configureChallengeRoutes()
    configureAdminRoutes()

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
    }
}
