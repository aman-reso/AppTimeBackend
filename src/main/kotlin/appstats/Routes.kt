package com.apptime.code.appstats

import com.apptime.code.common.MessageKeys
import com.apptime.code.common.respondApi
import com.apptime.code.common.respondError
import com.apptime.code.common.requireUserId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import users.UserRepository
import users.UserService

/**
 * Configure app stats routes
 */
fun Application.configureAppStatsRoutes() {
    val repository = AppStatsRepository()
    val service = AppStatsService(repository)
    val userRepository = UserRepository()
    val userService = UserService(userRepository)
    
    routing {
        route("/api/app-stats") {
            authenticate("auth-bearer") {
                /**
                 * POST /api/app-stats
                 * Add or update app stats for a user and date (requires authentication)
                 * If userId and date match an existing record, it will update the stats_json
                 * Otherwise, it will create a new record
                 * Request body: { "date": "2024-01-15", "stats": [...] }
                 */
                post {
                    try {
                        val currentUserId = call.requireUserId()
                        val request = call.receive<AddAppStatsRequest>()
                        
                        // Check if record exists to determine if it's an update or create
                        val existing = service.getAppStats(currentUserId, request.date)
                        val isUpdate = existing != null
                        
                        val response = service.addAppStats(
                            userId = currentUserId,
                            dateString = request.date,
                            stats = request.stats
                        )
                        
                        val messageKey = if (isUpdate) {
                            MessageKeys.APP_STATS_UPDATED
                        } else {
                            MessageKeys.APP_STATS_ADDED
                        }
                        
                        val statusCode = if (isUpdate) {
                            HttpStatusCode.OK
                        } else {
                            HttpStatusCode.Created
                        }
                        
                        call.respondApi(response, statusCode = statusCode, messageKey = messageKey)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.APP_STATS_ADD_FAILED, message = "Failed to add app stats: ${e.message}")
                    }
                }
                
                /**
                 * PUT /api/app-stats
                 * Update app stats for a user and date (requires authentication)
                 * Query params:
                 *   - userId (required): User ID
                 *   - date (required): Date in YYYY-MM-DD format
                 * Request body: { "stats": [...] }
                 */
                put {
                    try {
                        val currentUserId = call.requireUserId()
                        val userId = call.request.queryParameters["userId"]
                            ?: throw IllegalArgumentException("userId query parameter is required")
                        val date = call.request.queryParameters["date"]
                            ?: throw IllegalArgumentException("date query parameter is required")
                        
                        // Ensure user can only update stats for themselves
                        if (userId != currentUserId) {
                            throw IllegalArgumentException("You can only update stats for yourself")
                        }
                        
                        val request = call.receive<UpdateAppStatsRequest>()
                        
                        val response = service.updateAppStats(
                            userId = userId,
                            dateString = date,
                            stats = request.stats
                        )
                        
                        call.respondApi(response, messageKey = MessageKeys.APP_STATS_UPDATED)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.APP_STATS_UPDATE_FAILED, message = "Failed to update app stats: ${e.message}")
                    }
                }
                
                /**
                 * GET /api/app-stats
                 * Get app stats (requires authentication)
                 * Query params:
                 *   - targetUserName (optional): Target user's username (defaults to authenticated user)
                 *   - date (optional): Date in YYYY-MM-DD format (returns stats for specific date)
                 *   - startDate (optional): Start date for range query
                 *   - endDate (optional): End date for range query
                 * 
                 * If requesting another user's data, TOTP verification is required.
                 * If only date is provided, returns stats for that date
                 * If startDate and endDate are provided, returns stats in that range
                 * If no date params provided, returns all stats for the user
                 */
                get {
                    try {
                        val requestingUserId = call.requireUserId()
                        val targetUserName = call.request.queryParameters["targetUserName"]
                        
                        // Determine target user ID
                        val targetUserId = if (targetUserName != null) {
                            // Get target user ID by username
                            userRepository.getUserIdByUsername(targetUserName)
                                ?: throw IllegalArgumentException("User not found")
                        } else {
                            // Default to requesting user if no targetUserName provided
                            requestingUserId
                        }
                        
                        // If requesting another user's data, check TOTP verification
                        if (targetUserId != requestingUserId) {
                            if (!userService.hasAccessToUserData(requestingUserId, targetUserId)) {
                                throw SecurityException("Access denied. Please verify TOTP code first.")
                            }
                        }
                        
                        val date = call.request.queryParameters["date"]
                        val startDate = call.request.queryParameters["startDate"]
                        val endDate = call.request.queryParameters["endDate"]
                        
                        when {
                            // Single date query - return AppStatsResponse (has stats field)
                            date != null -> {
                                val statsResponse = service.getAppStats(targetUserId, date)
                                // If no data found, return empty stats array instead of error
                                if (statsResponse != null) {
                                    call.respondApi(statsResponse, messageKey = MessageKeys.APP_STATS_RETRIEVED)
                                } else {
                                    // Return empty stats response when data not available
                                    val emptyResponse = AppStatsResponse(stats = emptyList())
                                    call.respondApi(emptyResponse, messageKey = MessageKeys.APP_STATS_RETRIEVED)
                                }
                            }
                            // Date range query
                            startDate != null && endDate != null -> {
                                val response = service.getAppStatsByDateRange(targetUserId, startDate, endDate)
                                call.respondApi(response, messageKey = MessageKeys.APP_STATS_RETRIEVED)
                            }
                            // All stats for user
                            else -> {
                                val response = service.getAllAppStatsByUser(targetUserId)
                                call.respondApi(response, messageKey = MessageKeys.APP_STATS_RETRIEVED)
                            }
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: SecurityException) {
                        call.respondError(HttpStatusCode.Forbidden, messageKey = MessageKeys.ACCESS_DENIED, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.APP_STATS_FAILED, message = "Failed to fetch app stats: ${e.message}")
                    }
                }
            }
        }
    }
}

