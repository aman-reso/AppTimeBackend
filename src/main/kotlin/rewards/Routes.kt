package com.apptime.code.rewards

import com.apptime.code.challenges.ChallengeRepository
import com.apptime.code.common.respondApi
import com.apptime.code.common.respondError
import com.apptime.code.common.requireUserId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

/**
 * Configure reward-related routes
 */
fun Application.configureRewardRoutes() {
    val repository = RewardRepository()
    val challengeRepository = ChallengeRepository()
    val service = RewardService(repository, challengeRepository)
    
    routing {
        route("/api/rewards") {
            authenticate("auth-bearer") {
                /**
                 * GET /api/rewards
                 * Get all rewards for the authenticated user
                 * Query params:
                 *   - type (optional) - Filter by reward type (POINTS, BADGE, COUPON, TROPHY, CUSTOM)
                 *   - source (optional) - Filter by reward source (CHALLENGE_WIN, DAILY_LOGIN, etc.)
                 *   - claimed (optional) - Filter by claimed status (true/false)
                 *   - limit (optional) - Limit number of results
                 *   - offset (optional) - Offset for pagination
                 */
                get {
                    try {
                        val userId = call.requireUserId()
                        val type = call.request.queryParameters["type"]?.let {
                            try {
                                RewardType.valueOf(it.uppercase())
                            } catch (e: IllegalArgumentException) {
                                null
                            }
                        }
                        val source = call.request.queryParameters["source"]?.let {
                            try {
                                RewardSource.valueOf(it.uppercase())
                            } catch (e: IllegalArgumentException) {
                                null
                            }
                        }
                        val claimed = call.request.queryParameters["claimed"]?.toBoolean()
                        val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                        
                        val response = service.getUserRewards(
                            userId = userId,
                            type = type,
                            source = source,
                            isClaimed = claimed,
                            limit = limit,
                            offset = offset
                        )
                        call.respondApi(response, "User rewards retrieved successfully")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve rewards: ${e.message}")
                    }
                }
                
                /**
                 * GET /api/rewards/summary
                 * Get reward summary for the authenticated user
                 * Returns: Total points, badges, trophies, coupons, unclaimed count, and recent rewards
                 */
                get("/summary") {
                    try {
                        val userId = call.requireUserId()
                        val response = service.getRewardSummary(userId)
                        call.respondApi(response, "Reward summary retrieved successfully")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve reward summary: ${e.message}")
                    }
                }
                
                /**
                 * POST /api/rewards/claim
                 * Claim a reward (mark as claimed)
                 * Request body: { "rewardId": 1 }
                 */
                post("/claim") {
                    try {
                        val userId = call.requireUserId()
                        val request = call.receive<ClaimRewardRequest>()
                        
                        val response = service.claimReward(userId, request.rewardId)
                        call.respondApi(response, response.message)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: IllegalStateException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to claim reward: ${e.message}")
                    }
                }
                
                /**
                 * POST /api/rewards/create
                 * Create a new reward (admin or system use)
                 * Request body: CreateRewardRequest
                 */
                post("/create") {
                    try {
                        val request = call.receive<CreateRewardRequest>()
                        
                        // Optionally verify that the user can create rewards for themselves or is admin
                        val currentUserId = call.requireUserId()
                        if (request.userId != currentUserId) {
                            // In a real app, you'd check if user is admin here
                            // For now, we'll allow users to create rewards for themselves only
                            throw IllegalArgumentException("You can only create rewards for yourself")
                        }
                        
                        val reward = service.createReward(request)
                        call.respondApi(reward, "Reward created successfully", HttpStatusCode.Created)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to create reward: ${e.message}")
                    }
                }
                
                /**
                 * POST /api/rewards/challenge/award
                 * Award rewards for challenge winners (typically called after challenge ends)
                 * Request body: {
                 *   "challengeId": 1,
                 *   "topNRanks": 3  // Optional, defaults to 3
                 * }
                 */
                post("/challenge/award") {
                    try {
                        val request = call.receive<AwardChallengeRewardsRequest>()
                        
                        val response = service.awardChallengeRewards(
                            challengeId = request.challengeId,
                            topNRanks = request.topNRanks
                        )
                        call.respondApi(response, response.message, HttpStatusCode.Created)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: IllegalStateException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to award challenge rewards: ${e.message}")
                    }
                }
                
                /**
                 * GET /api/rewards/challenge/{challengeId}
                 * Get all rewards for a specific challenge
                 * Path parameter: challengeId
                 */
                get("/challenge/{challengeId}") {
                    try {
                        val challengeId = call.parameters["challengeId"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid challenge ID")
                        
                        val rewards = repository.getRewardsByChallenge(challengeId)
                        call.respondApi(
                            mapOf("challengeId" to challengeId, "rewards" to rewards),
                            "Challenge rewards retrieved successfully"
                        )
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve challenge rewards: ${e.message}")
                    }
                }
                
                /**
                 * GET /api/rewards/coins
                 * Get total coins earned and coin history for the authenticated user
                 * Query params:
                 *   - source (optional) - Filter by coin source (CHALLENGE_WIN, DAILY_LOGIN, etc.)
                 *   - limit (optional) - Limit number of history entries
                 *   - offset (optional) - Offset for pagination
                 */
                get("/coins") {
                    try {
                        val userId = call.requireUserId()
                        val source = call.request.queryParameters["source"]?.let {
                            try {
                                CoinSource.valueOf(it.uppercase())
                            } catch (e: IllegalArgumentException) {
                                null
                            }
                        }
                        val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                        
                        val response = service.getUserCoins(
                            userId = userId,
                            source = source,
                            limit = limit,
                            offset = offset
                        )
                        call.respondApi(response, "User coins retrieved successfully")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve coins: ${e.message}")
                    }
                }
                
                /**
                 * POST /api/rewards/coins/add
                 * Add coins to the authenticated user's account
                 * Request body: AddCoinsRequest
                 */
                post("/coins/add") {
                    try {
                        val currentUserId = call.requireUserId()
                        val request = call.receive<AddCoinsRequest>()
                        
                        // Ensure user can only add coins for themselves (unless admin)
                        if (request.userId != currentUserId) {
                            throw IllegalArgumentException("You can only add coins for yourself")
                        }
                        
                        val coin = service.addCoins(request)
                        call.respondApi(coin, "Coins added successfully", HttpStatusCode.Created)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to add coins: ${e.message}")
                    }
                }
                
                // ========== REWARD CATALOG ROUTES ==========
                
                /**
                 * GET /api/rewards/catalog
                 * Get all active reward catalog items
                 * Query params:
                 *   - category (optional) - Filter by category
                 */
                get("/catalog") {
                    try {
                        val category = call.request.queryParameters["category"]
                        val catalogItems = service.getActiveRewardCatalog(category)
                        call.respondApi(catalogItems, "Reward catalog retrieved successfully")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve catalog: ${e.message}")
                    }
                }
                
                /**
                 * GET /api/rewards/catalog/{catalogId}
                 * Get a specific reward catalog item
                 */
                get("/catalog/{catalogId}") {
                    try {
                        val catalogId = call.parameters["catalogId"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid catalog ID")
                        
                        val catalogItem = service.getRewardCatalogById(catalogId)
                            ?: throw IllegalArgumentException("Reward not found")
                        
                        call.respondApi(catalogItem, "Reward catalog item retrieved successfully")
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve catalog item: ${e.message}")
                    }
                }
                
                /**
                 * POST /api/rewards/catalog/claim
                 * Claim a reward (create a transaction)
                 * Request body: ClaimRewardCatalogRequest
                 */
                post("/catalog/claim") {
                    try {
                        val userId = call.requireUserId()
                        val request = call.receive<ClaimRewardCatalogRequest>()
                        
                        val response = service.claimRewardCatalog(userId, request)
                        call.respondApi(response, response.message, HttpStatusCode.Created)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: IllegalStateException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Cannot claim reward")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to claim reward: ${e.message}")
                    }
                }
                
                /**
                 * GET /api/rewards/transactions
                 * Get user's transaction history
                 * Query params:
                 *   - limit (optional) - Limit number of results
                 *   - offset (optional) - Offset for pagination
                 */
                get("/transactions") {
                    try {
                        val userId = call.requireUserId()
                        val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                        
                        val transactions = service.getUserTransactions(userId, limit, offset)
                        call.respondApi(transactions, "Transactions retrieved successfully")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve transactions: ${e.message}")
                    }
                }
                
                /**
                 * GET /api/rewards/transactions/{transactionId}
                 * Get a specific transaction
                 */
                get("/transactions/{transactionId}") {
                    try {
                        val userId = call.requireUserId()
                        val transactionId = call.parameters["transactionId"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid transaction ID")
                        
                        val transaction = service.getTransactionById(transactionId)
                            ?: throw IllegalArgumentException("Transaction not found")
                        
                        // Ensure user can only view their own transactions
                        if (transaction.userId != userId) {
                            throw IllegalArgumentException("Transaction not found")
                        }
                        
                        call.respondApi(transaction, "Transaction retrieved successfully")
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve transaction: ${e.message}")
                    }
                }
            }
        }
    }
}

