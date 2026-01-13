package com.apptime.code.leaderboard

import com.apptime.code.common.respondApi
import com.apptime.code.common.respondError
import com.apptime.code.common.requireUserId
import com.apptime.code.common.userId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

/**
 * Configure leaderboard-related routes
 */
fun Application.configureLeaderboardRoutes() {
    val repository = LeaderboardRepository()
    val service = LeaderboardService(repository)
    
    routing {
        route("/api/leaderboard") {
            /**
             * GET /api/leaderboard/daily
             * Get daily leaderboard (public, no auth required)
             * If userId provided or authenticated, includes userRank in response
             * Query params:
             *   - date (optional) - YYYY-MM-DD format. Defaults to today
             *   - userId (optional) - User ID to get rank for. If not provided, uses authenticated user's ID
             */
            get("/daily") {
                try {
                    val date = call.request.queryParameters["date"]
                    // Priority: query param userId > authenticated userId > null
                    val currentUserId = call.request.queryParameters["userId"] ?: call.userId
                    val leaderboard = service.getDailyLeaderboard(date, currentUserId)
                    call.respondApi(leaderboard, "Daily leaderboard retrieved successfully")
                } catch (e: IllegalArgumentException) {
                    call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                } catch (e: Exception) {
                    call.respondError(
                        HttpStatusCode.InternalServerError,
                        "Failed to retrieve daily leaderboard: ${e.message}"
                    )
                }
            }

            /**
             * GET /api/leaderboard/weekly
             * Get weekly leaderboard (public, no auth required)
             * If userId provided or authenticated, includes userRank in response
             * Query params:
             *   - weekDate (optional) - YYYY-WW format. Defaults to current week
             *   - userId (optional) - User ID to get rank for. If not provided, uses authenticated user's ID
             */
            get("/weekly") {
                try {
                    val weekDate = call.request.queryParameters["weekDate"]
                    // Priority: query param userId > authenticated userId > null
                    val currentUserId = call.request.queryParameters["userId"] ?: call.userId
                    val leaderboard = service.getWeeklyLeaderboard(weekDate, currentUserId)
                    call.respondApi(leaderboard, "Weekly leaderboard retrieved successfully")
                } catch (e: IllegalArgumentException) {
                    call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                } catch (e: Exception) {
                    call.respondError(
                        HttpStatusCode.InternalServerError,
                        "Failed to retrieve weekly leaderboard: ${e.message}"
                    )
                }
            }

            /**
             * GET /api/leaderboard/monthly
             * Get monthly leaderboard (public, no auth required)
             * Aggregates from daily stats - no separate monthly entries stored
             * If userId provided or authenticated, includes userRank in response
             * Query params:
             *   - monthDate (optional) - YYYY-MM format. Defaults to current month
             *   - userId (optional) - User ID to get rank for. If not provided, uses authenticated user's ID
             */
            get("/monthly") {
                try {
                    val monthDate = call.request.queryParameters["monthDate"]
                    // Priority: query param userId > authenticated userId > null
                    val currentUserId = call.request.queryParameters["userId"] ?: call.userId
                    val leaderboard = service.getMonthlyLeaderboard(monthDate, currentUserId)
                    call.respondApi(leaderboard, "Monthly leaderboard retrieved successfully")
                } catch (e: IllegalArgumentException) {
                    call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                } catch (e: Exception) {
                    call.respondError(
                        HttpStatusCode.InternalServerError,
                        "Failed to retrieve monthly leaderboard: ${e.message}"
                    )
                }
            }

            /**
             * POST /api/leaderboard/sync
             * Sync data from app_usage_events to leaderboardstats
             * Query param: date (optional) - YYYY-MM-DD format. If not provided, syncs all dates
             * This endpoint aggregates screen time from app_usage_events and stores it in leaderboardstats
             */
            post("/sync") {
                try {
                    val date = call.request.queryParameters["date"]
                    val result = service.syncFromAppUsageEvents(date)
                    call.respondApi(result, "Leaderboard stats synced successfully")
                } catch (e: IllegalArgumentException) {
                    call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                } catch (e: Exception) {
                    call.respondError(
                        HttpStatusCode.InternalServerError,
                        "Failed to sync leaderboard stats: ${e.message}"
                    )
                }
            }

            /**
             * POST /api/leaderboard/stats/update
             * Directly update leaderboard stats for authenticated user (requires authentication)
             * Only daily period is supported - weekly and monthly stats are automatically updated in the same call
             * Request body:
             *   - period: "daily" only (required)
             *   - periodDate: YYYY-MM-DD format (required)
             *   - totalScreenTime: Screen time in milliseconds (required)
             *   - replace: Boolean, if true replaces existing value, if false adds to existing (default: false)
             */
            authenticate("auth-bearer") {
                post("/stats/update") {
                    try {
                        val userId = call.requireUserId()
                        val request = call.receive<UpdateLeaderboardStatsRequest>()

                        val response = service.updateLeaderboardStats(
                            userId = userId,
                            period = request.period,
                            periodDate = request.periodDate,
                            totalScreenTime = request.totalScreenTime,
                            replace = request.replace
                        )

                        call.respondApi(response, response.message)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(
                            HttpStatusCode.InternalServerError,
                            "Failed to update leaderboard stats: ${e.message}"
                        )
                    }
                }
            }
        }
        route("/api/summary") {
            /**
             * POST /api/summary/screentime
             * Get daily screen time for a list of username-date pairs from leaderboard stats (requires authentication)
             * Returns daily screen time for each username for their specified date
             * Only returns data for usernames that have verified the authenticated user via TOTP
             * Request body:
             *   - users: List of username-date pairs, each containing:
             *     - username: String (required)
             *     - date: String in YYYY-MM-DD format (required)
             * Response includes username, date, and screentime in milliseconds (null if no data)
             * Only includes users who have verified the authenticated user via TOTP
             */
            authenticate("auth-bearer") {
                post("/screentime") {
                    try {
                        val authenticatedUserId = call.requireUserId() // Ensure user is authenticated
                        val request = call.receive<GetScreenTimeByUsernamesRequest>()

                        val response = service.getScreenTimeByUsernames(request.users, authenticatedUserId)
                        call.respondApi(response, "Screen time retrieved successfully")
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(
                            HttpStatusCode.InternalServerError,
                            "Failed to retrieve screen time: ${e.message}"
                        )
                    }
                }
            }
        }
    }
}

