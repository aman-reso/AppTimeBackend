package com.apptime.code.admin

import com.apptime.code.common.EnvLoader
import com.apptime.code.common.respondApi
import com.apptime.code.common.respondError
import com.apptime.code.rewards.RewardService
import com.apptime.code.rewards.RewardRepository
import com.apptime.code.rewards.TransactionStatus
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.util.*

/**
 * Configure admin-related routes
 */
fun Application.configureAdminRoutes() {
    val statsRepository = StatsRepository()
    val statsService = AdminService(statsRepository)
    val adminRepository = AdminRepository()
    val rewardRepository = RewardRepository()
    val rewardService = RewardService(rewardRepository)
    
    routing {
        route("/api/admin") {
            /**
             * POST /api/admin/login
             * Admin login endpoint
             */
            post("/login") {
                try {
                    val request = call.receive<AdminLoginRequest>()
                    val adminUsername = EnvLoader.getEnv("ADMIN_USERNAME", "admin")
                    val adminPassword = EnvLoader.getEnv("ADMIN_PASSWORD", "admin123")
                    
                    if (request.username == adminUsername && request.password == adminPassword) {
                        // Generate a simple session token
                        val sessionToken = UUID.randomUUID().toString()
                        val loginResponse = AdminLoginResponse(token = sessionToken)
                        call.respondApi(loginResponse, "Login successful")
                    } else {
                        call.respondError(HttpStatusCode.Unauthorized, "Invalid username or password")
                    }
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.BadRequest, "Invalid request: ${e.message}")
                }
            }
            
            /**
             * POST /api/admin/verify
             * Verify admin session token
             */
            post("/verify") {
                try {
                    val request = call.receive<AdminVerifyRequest>()
                    // For simplicity, we'll accept any non-empty token
                    // In production, you should validate tokens properly
                    if (request.token.isNotBlank()) {
                        val verifyResponse = AdminVerifyResponse(valid = true)
                        call.respondApi(verifyResponse, "Token is valid")
                    } else {
                        call.respondError(HttpStatusCode.Unauthorized, "Invalid token")
                    }
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.BadRequest, "Invalid request: ${e.message}")
                }
            }
            
            /**
             * GET /api/admin/stats
             * Get comprehensive admin statistics
             */
            get("/stats") {
                try {
                    val stats = statsService.getAdminStats()
                    call.respondApi(stats, "Admin statistics retrieved successfully")
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve admin statistics: ${e.message}")
                }
            }
            
            // Challenge Management
            route("/challenges") {
                // Get all challenges
                get {
                    try {
                        val challenges = adminRepository.getAllChallenges()
                        call.respondApi(challenges, "Challenges retrieved successfully")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve challenges: ${e.message}")
                    }
                }
                
                // Get challenge by ID
                get("/{id}") {
                    try {
                        val id = call.parameters["id"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid challenge ID")
                        val challenge = adminRepository.getChallengeById(id)
                        if (challenge != null) {
                            call.respondApi(challenge, "Challenge retrieved successfully")
                        } else {
                            call.respondError(HttpStatusCode.NotFound, "Challenge not found")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve challenge: ${e.message}")
                    }
                }
                
                // Create challenge
                post {
                    try {
                        val request = call.receive<CreateChallengeRequest>()
                        val id = adminRepository.createChallenge(request)
                        val challenge = adminRepository.getChallengeById(id)
                        call.respondApi(challenge, "Challenge created successfully", HttpStatusCode.Created)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to create challenge: ${e.message}")
                    }
                }
                
                // Update challenge
                put("/{id}") {
                    try {
                        val id = call.parameters["id"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid challenge ID")
                        val request = call.receive<UpdateChallengeRequest>()
                        val updated = adminRepository.updateChallenge(id, request)
                        if (updated) {
                            val challenge = adminRepository.getChallengeById(id)
                            call.respondApi(challenge, "Challenge updated successfully")
                        } else {
                            call.respondError(HttpStatusCode.NotFound, "Challenge not found")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to update challenge: ${e.message}")
                    }
                }
                
                // Delete challenge
                delete("/{id}") {
                    try {
                        val id = call.parameters["id"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid challenge ID")
                        val deleted = adminRepository.deleteChallenge(id)
                        if (deleted) {
                            call.respondApi("", "Challenge deleted successfully")
                        } else {
                            call.respondError(HttpStatusCode.NotFound, "Challenge not found")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to delete challenge: ${e.message}")
                    }
                }
            }
            
            // User Management
            route("/users") {
                // Get all users
                get {
                    try {
                        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                        val users = adminRepository.getAllUsers(limit, offset)
                        call.respondApi(users, "Users retrieved successfully")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve users: ${e.message}")
                    }
                }
                
                // Get user by ID
                get("/{userId}") {
                    try {
                        val userId = call.parameters["userId"]
                            ?: throw IllegalArgumentException("Invalid user ID")
                        val user = adminRepository.getUserById(userId)
                        if (user != null) {
                            call.respondApi(user, "User retrieved successfully")
                        } else {
                            call.respondError(HttpStatusCode.NotFound, "User not found")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve user: ${e.message}")
                    }
                }
                
                // Update user
                put("/{userId}") {
                    try {
                        val userId = call.parameters["userId"]
                            ?: throw IllegalArgumentException("Invalid user ID")
                        val request = call.receive<UpdateUserRequest>()
                        val updated = adminRepository.updateUser(userId, request)
                        if (updated) {
                            val user = adminRepository.getUserById(userId)
                            call.respondApi(user, "User updated successfully")
                        } else {
                            call.respondError(HttpStatusCode.NotFound, "User not found")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to update user: ${e.message}")
                    }
                }
                
                // Block user
                post("/{userId}/block") {
                    try {
                        val userId = call.parameters["userId"]
                            ?: throw IllegalArgumentException("Invalid user ID")
                        val blocked = adminRepository.blockUser(userId)
                        if (blocked) {
                            val user = adminRepository.getUserById(userId)
                            call.respondApi(user, "User blocked successfully")
                        } else {
                            call.respondError(HttpStatusCode.NotFound, "User not found")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to block user: ${e.message}")
                    }
                }
                
                // Unblock user
                post("/{userId}/unblock") {
                    try {
                        val userId = call.parameters["userId"]
                            ?: throw IllegalArgumentException("Invalid user ID")
                        val unblocked = adminRepository.unblockUser(userId)
                        if (unblocked) {
                            val user = adminRepository.getUserById(userId)
                            call.respondApi(user, "User unblocked successfully")
                        } else {
                            call.respondError(HttpStatusCode.NotFound, "User not found")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to unblock user: ${e.message}")
                    }
                }
                
                // Delete user
                delete("/{userId}") {
                    try {
                        val userId = call.parameters["userId"]
                            ?: throw IllegalArgumentException("Invalid user ID")
                        val deleted = adminRepository.deleteUser(userId)
                        if (deleted) {
                            call.respondApi("", "User deleted successfully")
                        } else {
                            call.respondError(HttpStatusCode.NotFound, "User not found")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to delete user: ${e.message}")
                    }
                }
            }
            
            // Reward Management
            route("/rewards") {
                // Get all rewards
                get {
                    try {
                        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                        val rewards = adminRepository.getAllRewards(limit, offset)
                        call.respondApi(rewards, "Rewards retrieved successfully")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve rewards: ${e.message}")
                    }
                }
                
                // Get reward by ID
                get("/{id}") {
                    try {
                        val id = call.parameters["id"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid reward ID")
                        val reward = adminRepository.getRewardById(id)
                        if (reward != null) {
                            call.respondApi(reward, "Reward retrieved successfully")
                        } else {
                            call.respondError(HttpStatusCode.NotFound, "Reward not found")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve reward: ${e.message}")
                    }
                }
                
                // Create reward
                post {
                    try {
                        val request = call.receive<CreateRewardRequest>()
                        val id = adminRepository.createReward(request)
                        val reward = adminRepository.getRewardById(id)
                        call.respondApi(reward, "Reward created successfully", HttpStatusCode.Created)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to create reward: ${e.message}")
                    }
                }
                
                // Update reward
                put("/{id}") {
                    try {
                        val id = call.parameters["id"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid reward ID")
                        val request = call.receive<UpdateRewardRequest>()
                        val updated = adminRepository.updateReward(id, request)
                        if (updated) {
                            val reward = adminRepository.getRewardById(id)
                            call.respondApi(reward, "Reward updated successfully")
                        } else {
                            call.respondError(HttpStatusCode.NotFound, "Reward not found")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to update reward: ${e.message}")
                    }
                }
                
                // Delete reward
                delete("/{id}") {
                    try {
                        val id = call.parameters["id"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid reward ID")
                        val deleted = adminRepository.deleteReward(id)
                        if (deleted) {
                            call.respondApi("", "Reward deleted successfully")
                        } else {
                            call.respondError(HttpStatusCode.NotFound, "Reward not found")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to delete reward: ${e.message}")
                    }
                }
            }
            
            // Consent Template Management
            route("/consents") {
                // Get all consent templates
                get {
                    try {
                        val templates = adminRepository.getAllConsentTemplates()
                        call.respondApi(templates, "Consent templates retrieved successfully")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve consent templates: ${e.message}")
                    }
                }
                
                // Get consent template by ID
                get("/{id}") {
                    try {
                        val id = call.parameters["id"]?.toIntOrNull()
                            ?: throw IllegalArgumentException("Invalid consent template ID")
                        val template = adminRepository.getConsentTemplateById(id)
                        if (template != null) {
                            call.respondApi(template, "Consent template retrieved successfully")
                        } else {
                            call.respondError(HttpStatusCode.NotFound, "Consent template not found")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve consent template: ${e.message}")
                    }
                }
                
                // Create consent template
                post {
                    try {
                        val request = call.receive<CreateConsentTemplateRequest>()
                        val id = adminRepository.createConsentTemplate(request)
                        val template = adminRepository.getConsentTemplateById(id)
                        call.respondApi(template, "Consent template created successfully", HttpStatusCode.Created)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to create consent template: ${e.message}")
                    }
                }
                
                // Update consent template
                put("/{id}") {
                    try {
                        val id = call.parameters["id"]?.toIntOrNull()
                            ?: throw IllegalArgumentException("Invalid consent template ID")
                        val request = call.receive<UpdateConsentTemplateRequest>()
                        val updated = adminRepository.updateConsentTemplate(id, request)
                        if (updated) {
                            val template = adminRepository.getConsentTemplateById(id)
                            call.respondApi(template, "Consent template updated successfully")
                        } else {
                            call.respondError(HttpStatusCode.NotFound, "Consent template not found")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to update consent template: ${e.message}")
                    }
                }
                
                // Delete consent template
                delete("/{id}") {
                    try {
                        val id = call.parameters["id"]?.toIntOrNull()
                            ?: throw IllegalArgumentException("Invalid consent template ID")
                        val deleted = adminRepository.deleteConsentTemplate(id)
                        if (deleted) {
                            call.respondApi("", "Consent template deleted successfully")
                        } else {
                            call.respondError(HttpStatusCode.NotFound, "Consent template not found")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to delete consent template: ${e.message}")
                    }
                }
            }
            
            // Reward Catalog Management
            route("/catalog") {
                // Get all catalog items
                get {
                    try {
                        val category = call.request.queryParameters["category"]
                        val catalogItems = rewardService.getActiveRewardCatalog(category)
                        call.respondApi(catalogItems, "Reward catalog retrieved successfully")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve catalog: ${e.message}")
                    }
                }
                
                // Get catalog item by ID
                get("/{id}") {
                    try {
                        val id = call.parameters["id"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid catalog ID")
                        val catalogItem = rewardService.getRewardCatalogById(id)
                            ?: throw IllegalArgumentException("Catalog item not found")
                        call.respondApi(catalogItem, "Catalog item retrieved successfully")
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve catalog item: ${e.message}")
                    }
                }
                
                // Create catalog item
                post {
                    try {
                        val request = call.receive<com.apptime.code.rewards.CreateRewardCatalogRequest>()
                        val catalogItem = rewardService.createRewardCatalogItem(request)
                        call.respondApi(catalogItem, "Catalog item created successfully", HttpStatusCode.Created)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to create catalog item: ${e.message}")
                    }
                }
            }
            
            // Transaction/Order Management
            route("/transactions") {
                // Get all transactions
                get {
                    try {
                        val status = call.request.queryParameters["status"]?.let {
                            try {
                                TransactionStatus.valueOf(it.uppercase())
                            } catch (e: IllegalArgumentException) {
                                null
                            }
                        }
                        val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                        
                        val transactions = rewardService.getAllTransactions(status, limit, offset)
                        call.respondApi(transactions, "Transactions retrieved successfully")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve transactions: ${e.message}")
                    }
                }
                
                // Get transaction by ID
                get("/{id}") {
                    try {
                        val id = call.parameters["id"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid transaction ID")
                        val transaction = rewardService.getTransactionById(id)
                            ?: throw IllegalArgumentException("Transaction not found")
                        call.respondApi(transaction, "Transaction retrieved successfully")
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to retrieve transaction: ${e.message}")
                    }
                }
                
                // Update transaction status
                put("/{id}/status") {
                    try {
                        val id = call.parameters["id"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid transaction ID")
                        val request = call.receive<com.apptime.code.rewards.UpdateTransactionStatusRequest>()
                        val transaction = rewardService.updateTransactionStatus(id, request)
                        call.respondApi(transaction, "Transaction status updated successfully")
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to update transaction status: ${e.message}")
                    }
                }
            }
        }
    }
}
