package com.apptime.code.challenges

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
 * Configure challenge-related routes
 */
fun Application.configureChallengeRoutes() {
    val repository = ChallengeRepository()
    val service = ChallengeService(repository)
    
    routing {
        route("/api/challenges") {
            authenticate("auth-bearer", optional = true) {
                /**
                 * GET /api/challenges/active
                 * Get all active challenges (public endpoint, auth optional)
                 * Returns: List of active challenges with title, description, reward, startTime, endTime, thumbnail
                 * If the user is authenticated, each challenge includes hasJoined flag.
                 */
                get("/active") {
                    try {
                        val response = service.getActiveChallenges(call.userId)
                        call.respondApi(response, "Active challenges retrieved successfully")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve active challenges: ${e.message}")
                    }
                }
            }
            
            authenticate("auth-bearer") {
                /**
                 * POST /api/challenges/join
                 * Register/join a challenge (requires authentication)
                 * Request body: { "challengeId": 1 }
                 */
                post("/join") {
                    try {
                        val userId = call.requireUserId()
                        val request = call.receive<JoinChallengeRequest>()
                        
                        val response = service.joinChallenge(userId, request.challengeId)
                        call.respondApi(response, response.message, HttpStatusCode.Created)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to join challenge: ${e.message}")
                    }
                }
                
                /**
                 * GET /api/challenges/user
                 * Get all challenges for the authenticated user (including past ones)
                 * Returns: List of challenges with joinedAt timestamp and isPast flag
                 */
                get("/user") {
                    try {
                        val userId = call.requireUserId()
                        val response = service.getUserChallenges(userId)
                        call.respondApi(response, "User challenges retrieved successfully")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve user challenges: ${e.message}")
                    }
                }
                
                /**
                 * GET /api/challenges/{challengeId}
                 * Get challenge details including participant count and hasJoined flag (requires authentication)
                 * Path parameter: challengeId
                 * Returns: Challenge details with hasJoined field indicating if current user has joined
                 */
                get("/{challengeId}") {
                    try {
                        val challengeId = call.parameters["challengeId"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid challenge ID")
                        
                        val userId = call.userId
                        val response = service.getChallengeDetail(challengeId, userId)
                        call.respondApi(response, "Challenge details retrieved successfully")
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve challenge details: ${e.message}")
                    }
                }
                
                /**
                 * POST /api/challenges/stats
                 * Submit challenge participant stats (requires authentication)
                 * Request body: {
                 *   "challengeId": 1,
                 *   "appName": "Instagram",
                 *   "packageName": "com.instagram.android",
                 *   "startSyncTime": "2024-01-15T10:00:00Z",
                 *   "endSyncTime": "2024-01-15T10:30:00Z",
                 *   "duration": 1800000
                 * }
                 */
                post("/stats") {
                    try {
                        val userId = call.requireUserId()
                        val request = call.receive<ChallengeStatsSubmissionRequest>()
                        
                        service.submitChallengeStats(userId, request)
                        call.respondApi(
                            mapOf("message" to "Challenge stats submitted successfully"),
                            "Challenge stats submitted successfully",
                            HttpStatusCode.Created
                        )
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to submit challenge stats: ${e.message}")
                    }
                }
                
                /**
                 * POST /api/challenges/stats/batch
                 * Submit multiple challenge participant stats (requires authentication)
                 * Request body: {
                 *   "challengeId": 1,
                 *   "stats": [
                 *     {
                 *       "challengeId": 1,
                 *       "appName": "Instagram",
                 *       "packageName": "com.instagram.android",
                 *       "startSyncTime": "2024-01-15T10:00:00Z",
                 *       "endSyncTime": "2024-01-15T10:30:00Z",
                 *       "duration": 1800000
                 *     }
                 *   ]
                 * }
                 */
                post("/stats/batch") {
                    try {
                        val userId = call.requireUserId()
                        val request = call.receive<BatchChallengeStatsSubmissionRequest>()
                        
                        val response = service.submitBatchChallengeStats(
                            userId,
                            request.challengeId,
                            request.stats
                        )
                        call.respondApi(response, "Challenge stats submitted successfully", HttpStatusCode.Created)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to submit challenge stats: ${e.message}")
                    }
                }
                
                /**
                 * GET /api/challenges/{challengeId}/rankings
                 * Get challenge rankings - top 10 players with sum of duration (requires authentication)
                 * Ranking calculation:
                 * - For LESS_SCREENTIME challenges: ranked by total duration ascending (lower is better)
                 * - For MORE_SCREENTIME challenges: ranked by total duration descending (higher is better)
                 * Path parameter: challengeId
                 * Returns: Top 10 rankings and current user's rank if they're participating
                 */
                get("/{challengeId}/rankings") {
                    try {
                        val userId = call.requireUserId()
                        val challengeId = call.parameters["challengeId"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid challenge ID")
                        
                        val response = service.getChallengeRankings(challengeId, userId)
                        call.respondApi(response, "Challenge rankings retrieved successfully")
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve challenge rankings: ${e.message}")
                    }
                }
                
                /**
                 * GET /api/challenges/{challengeId}/last-sync
                 * Get last sync time for the authenticated user in a specific challenge (requires authentication)
                 * Path parameter: challengeId
                 * Returns: Last sync time (endSyncTime of most recent stat submission) or null if no stats submitted
                 */
                get("/{challengeId}/last-sync") {
                    try {
                        val userId = call.requireUserId()
                        val challengeId = call.parameters["challengeId"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid challenge ID")
                        
                        val response = service.getLastSyncTime(userId, challengeId)
                        call.respondApi(response, "Last sync time retrieved successfully")
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve last sync time: ${e.message}")
                    }
                }
            }
        }
    }
}

