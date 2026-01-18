package com.apptime.code.focus

import com.apptime.code.common.MessageKeys
import com.apptime.code.common.respondApi
import com.apptime.code.common.respondError
import com.apptime.code.common.requireUserId
import com.apptime.code.leaderboard.LeaderboardRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

/**
 * Configure focus-related routes
 */
fun Application.configureFocusRoutes() {
    val repository = FocusRepository()
    val leaderboardRepository = LeaderboardRepository()
    val notificationRepository = com.apptime.code.notifications.NotificationRepository()
    val userRepository = users.UserRepository()
    val notificationService = com.apptime.code.notifications.NotificationService(notificationRepository, userRepository)
    val service = FocusService(repository, leaderboardRepository, notificationService)
    
    // Focus mode stats
    val focusModeStatsRepository = FocusModeStatsRepository()
    val focusModeStatsService = FocusModeStatsService(focusModeStatsRepository)
    
    routing {
        route("/api/focus") {
            authenticate("auth-bearer") {
                /**
                 * POST /api/focus/submit
                 * Submit focus sessions (single or array) (requires authentication)
                 * Accepts either:
                 * - Single session: { "focusDuration": ..., "startTime": ..., "endTime": ..., "sessionType": ... }
                 * - Array of sessions: { "sessions": [{ ... }, { ... }] }
                 */
                post("/submit") {
                    try {
                        val userId = call.requireUserId()
                        
                        // Read raw JSON to check structure
                        val jsonString = call.receive<String>()
                        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                        
                        // Try to parse as array request first (check if it has "sessions" key)
                        val result = if (jsonString.contains("\"sessions\"") || jsonString.trimStart().startsWith("[")) {
                            // Array submission
                            val arrayRequest = json.decodeFromString<FocusDurationSubmissionArrayRequest>(jsonString)
                            service.submitFocusSessions(userId, arrayRequest.sessions)
                        } else {
                            // Single session submission (backward compatible)
                            val singleRequest = json.decodeFromString<FocusDurationSubmissionRequest>(jsonString)
                            service.submitFocusSessions(userId, listOf(singleRequest))
                        }
                        
                        val messageKey = if (result.totalSubmitted == 1) {
                            MessageKeys.FOCUS_SESSION_SUBMITTED
                        } else {
                            MessageKeys.FOCUS_SESSIONS_SUBMITTED
                        }
                        call.respondApi(result, statusCode = HttpStatusCode.Created, messageKey = messageKey)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: kotlinx.serialization.SerializationException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_JSON, message = "Invalid JSON format: ${e.message}")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.FOCUS_SUBMIT_FAILED, message = "Failed to submit focus session: ${e.message}")
                    }
                }
                
                /**
                 * GET /api/focus/history
                 * Get focus history for authenticated user (requires authentication)
                 * Query params: 
                 *   - startDate (optional): ISO 8601 format (e.g., 2024-01-15T00:00:00Z)
                 *   - endDate (optional): ISO 8601 format (e.g., 2024-01-15T23:59:59Z)
                 * If no dates provided, returns all sessions
                 */
                get("/history") {
                    try {
                        val userId = call.requireUserId()
                        val startDate = call.request.queryParameters["startDate"]
                        val endDate = call.request.queryParameters["endDate"]
                        
                        val history = service.getFocusHistory(userId, startDate, endDate)
                        val messageKey = if (startDate != null || endDate != null) {
                            MessageKeys.FOCUS_HISTORY_RANGE_RETRIEVED
                        } else {
                            MessageKeys.FOCUS_HISTORY_RETRIEVED
                        }
                        call.respondApi(history, messageKey = messageKey)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.FOCUS_HISTORY_FAILED, message = "Failed to retrieve focus history: ${e.message}")
                    }
                }
                
                /**
                 * GET /api/focus/stats
                 * Get focus statistics for authenticated user (requires authentication)
                 */
                get("/stats") {
                    try {
                        val userId = call.requireUserId()
                        val stats = service.getFocusStats(userId)
                        call.respondApi(stats, messageKey = MessageKeys.FOCUS_STATS_RETRIEVED)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.FOCUS_STATS_FAILED, message = "Failed to retrieve focus stats: ${e.message}")
                    }
                }
            }
            
            // Focus Mode Stats routes
            route("/api/focus-mode-stats") {
                authenticate("auth-bearer") {
                    /**
                     * POST /api/focus-mode-stats
                     * Submit focus mode stats (requires authentication)
                     * Request body: { "startTime": 1234567890000, "endTime": 1234567895000 }
                     * Times are in milliseconds
                     */
                    post {
                        try {
                            val userId = call.requireUserId()
                            val request = call.receive<FocusModeStatsRequest>()
                            
                            val response = focusModeStatsService.saveFocusModeStats(
                                userId = userId,
                                startTime = request.startTime,
                                endTime = request.endTime
                            )
                            
                            call.respondApi(response, statusCode = HttpStatusCode.Created, messageKey = MessageKeys.FOCUS_MODE_STATS_SAVED)
                        } catch (e: IllegalArgumentException) {
                            call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                        } catch (e: Exception) {
                            call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.FOCUS_MODE_STATS_SAVE_FAILED, message = "Failed to save focus mode stats: ${e.message}")
                        }
                    }
                    
                    /**
                     * GET /api/focus-mode-stats
                     * Get focus mode stats with optional time-based filtering (requires authentication)
                     * Query params:
                     *   - startTimeMs (optional): Filter stats with startTime >= this value (milliseconds)
                     *   - endTimeMs (optional): Filter stats with endTime <= this value (milliseconds)
                     * If no filters provided, returns all stats for the user
                     */
                    get {
                        try {
                            val userId = call.requireUserId()
                            val startTimeMs = call.request.queryParameters["startTimeMs"]?.toLongOrNull()
                            val endTimeMs = call.request.queryParameters["endTimeMs"]?.toLongOrNull()
                            
                            val response = focusModeStatsService.getFocusModeStats(
                                userId = userId,
                                startTimeMs = startTimeMs,
                                endTimeMs = endTimeMs
                            )
                            
                            val messageKey = when {
                                startTimeMs != null && endTimeMs != null -> MessageKeys.FOCUS_MODE_STATS_RANGE_RETRIEVED
                                startTimeMs != null -> MessageKeys.FOCUS_MODE_STATS_FROM_RETRIEVED
                                endTimeMs != null -> MessageKeys.FOCUS_MODE_STATS_UNTIL_RETRIEVED
                                else -> MessageKeys.FOCUS_MODE_STATS_RETRIEVED
                            }
                            
                            call.respondApi(response, messageKey = messageKey)
                        } catch (e: IllegalArgumentException) {
                            call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                        } catch (e: Exception) {
                            call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.FOCUS_MODE_STATS_FAILED, message = "Failed to retrieve focus mode stats: ${e.message}")
                        }
                    }
                }
            }
        }
    }
}

