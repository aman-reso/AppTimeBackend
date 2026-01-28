package com.apptime.code.admin

import com.apptime.code.common.EnvLoader
import com.apptime.code.common.MessageKeys
import com.apptime.code.common.respondApi
import com.apptime.code.common.respondError
import com.apptime.code.feedback.FeedbackRepository
import feedback.FeedbackService
import com.apptime.code.feedback.UpdateFeedbackStatusRequest
import com.apptime.code.rewards.RewardService
import com.apptime.code.rewards.RewardRepository
import com.apptime.code.rewards.TransactionStatus
import com.apptime.code.rewards.CoinSource
import com.apptime.code.rewards.AddCoinsRequest
import com.apptime.code.notifications.NotificationService
import com.apptime.code.notifications.NotificationRepository
import users.UserRepository
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import java.util.*
import java.io.File

/**
 * Configure admin-related routes
 */
fun Application.configureAdminRoutes() {
    val statsRepository = StatsRepository()
    val statsService = AdminService(statsRepository)
    val adminRepository = AdminRepository()
    val rewardRepository = RewardRepository()
    val rewardService = RewardService(rewardRepository)
    val feedbackRepository = FeedbackRepository()
    val feedbackService = FeedbackService(feedbackRepository)
    val userRepository = UserRepository()
    val notificationRepository = NotificationRepository()
    val notificationService = NotificationService(notificationRepository, userRepository)
    
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
                        call.respondApi(loginResponse, messageKey = MessageKeys.ADMIN_LOGIN_SUCCESS)
                    } else {
                        call.respondError(HttpStatusCode.Unauthorized, messageKey = MessageKeys.ADMIN_LOGIN_INVALID)
                    }
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = "Invalid request: ${e.message}")
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
                        call.respondApi(verifyResponse, messageKey = MessageKeys.ADMIN_TOKEN_VALID)
                    } else {
                        call.respondError(HttpStatusCode.Unauthorized, messageKey = MessageKeys.ADMIN_TOKEN_INVALID)
                    }
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = "Invalid request: ${e.message}")
                }
            }
            
            /**
             * GET /api/admin/stats
             * Get comprehensive admin statistics
             */
            get("/stats") {
                try {
                    val stats = statsService.getAdminStats()
                    call.respondApi(stats, messageKey = MessageKeys.ADMIN_STATS_RETRIEVED)
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.ADMIN_STATS_FAILED, message = "Failed to retrieve admin statistics: ${e.message}")
                }
            }
            
            /**
             * GET /api/admin/users-stats
             * Get paginated user statistics with search
             * Query params: username (optional), page (default: 1), pageSize (default: 20, max: 100)
             */
            get("/users-stats") {
                try {
                    val username = call.request.queryParameters["username"]
                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                    val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
                    
                    val paginatedUsers = statsService.getUsersWithPagination(username, page, pageSize)
                    call.respondApi(paginatedUsers, messageKey = MessageKeys.USERS_RETRIEVED)
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.USERS_FAILED, message = "Failed to retrieve paginated user stats: ${e.message}")
                }
            }
            
            // Challenge Management
            route("/challenges") {
                // Get all challenges
                get {
                    try {
                        val challenges = adminRepository.getAllChallenges()
                        call.respondApi(challenges, messageKey = MessageKeys.CHALLENGES_RETRIEVED)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.CHALLENGES_FAILED, message = "Failed to retrieve challenges: ${e.message}")
                    }
                }
                
                // Get challenge by ID
                get("/{id}") {
                    try {
                        val id = call.parameters["id"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid challenge ID")
                        val challenge = adminRepository.getChallengeById(id)
                        if (challenge != null) {
                            call.respondApi(challenge, messageKey = MessageKeys.CHALLENGE_RETRIEVED)
                        } else {
                            call.respondError(HttpStatusCode.NotFound, messageKey = MessageKeys.CHALLENGE_NOT_FOUND)
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.CHALLENGES_FAILED, message = "Failed to retrieve challenge: ${e.message}")
                    }
                }
                
                // Create challenge
                post {
                    try {
                        val request = call.receive<CreateChallengeRequest>()
                        val id = adminRepository.createChallenge(request)
                        val challenge = adminRepository.getChallengeById(id)
                        call.respondApi(challenge, statusCode = HttpStatusCode.Created, messageKey = MessageKeys.CHALLENGE_CREATED)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.CHALLENGES_FAILED, message = "Failed to create challenge: ${e.message}")
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
                            call.respondApi(challenge, messageKey = MessageKeys.CHALLENGE_UPDATED)
                        } else {
                            call.respondError(HttpStatusCode.NotFound, messageKey = MessageKeys.CHALLENGE_NOT_FOUND)
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.CHALLENGES_FAILED, message = "Failed to update challenge: ${e.message}")
                    }
                }
                
                // Delete challenge
                delete("/{id}") {
                    try {
                        val id = call.parameters["id"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid challenge ID")
                        val deleted = adminRepository.deleteChallenge(id)
                        if (deleted) {
                            call.respondApi("", messageKey = MessageKeys.CHALLENGE_DELETED)
                        } else {
                            call.respondError(HttpStatusCode.NotFound, messageKey = MessageKeys.CHALLENGE_NOT_FOUND)
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.CHALLENGES_FAILED, message = "Failed to delete challenge: ${e.message}")
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
                        call.respondApi(users, messageKey = MessageKeys.USERS_RETRIEVED)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.USERS_FAILED, message = "Failed to retrieve users: ${e.message}")
                    }
                }
                
                // Get user by ID
                get("/{userId}") {
                    try {
                        val userId = call.parameters["userId"]
                            ?: throw IllegalArgumentException("Invalid user ID")
                        val user = adminRepository.getUserById(userId)
                        if (user != null) {
                            call.respondApi(user, messageKey = MessageKeys.USER_RETRIEVED)
                        } else {
                            call.respondError(HttpStatusCode.NotFound, messageKey = MessageKeys.USER_NOT_FOUND)
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.USERS_FAILED, message = "Failed to retrieve user: ${e.message}")
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
                            call.respondApi(user, messageKey = MessageKeys.USER_UPDATED)
                        } else {
                            call.respondError(HttpStatusCode.NotFound, messageKey = MessageKeys.USER_NOT_FOUND)
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.USERS_FAILED, message = "Failed to update user: ${e.message}")
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
                            call.respondApi(user, messageKey = MessageKeys.USER_BLOCKED)
                        } else {
                            call.respondError(HttpStatusCode.NotFound, messageKey = MessageKeys.USER_NOT_FOUND)
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.USERS_FAILED, message = "Failed to block user: ${e.message}")
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
                            call.respondApi(user, messageKey = MessageKeys.USER_UNBLOCKED)
                        } else {
                            call.respondError(HttpStatusCode.NotFound, messageKey = MessageKeys.USER_NOT_FOUND)
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.USERS_FAILED, message = "Failed to unblock user: ${e.message}")
                    }
                }
                
                // Delete user
                delete("/{userId}") {
                    try {
                        val userId = call.parameters["userId"]
                            ?: throw IllegalArgumentException("Invalid user ID")
                        val deleted = adminRepository.deleteUser(userId)
                        if (deleted) {
                            call.respondApi("", messageKey = MessageKeys.USER_DELETED)
                        } else {
                            call.respondError(HttpStatusCode.NotFound, messageKey = MessageKeys.USER_NOT_FOUND)
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.USERS_FAILED, message = "Failed to delete user: ${e.message}")
                    }
                }
                
                // Add coins to user
                post("/{userId}/coins") {
                    try {
                        val userId = call.parameters["userId"]
                            ?: throw IllegalArgumentException("Invalid user ID")
                        val request = call.receive<AddCoinsToUserRequest>()
                        
                        // Validate amount
                        if (request.amount <= 0) {
                            throw IllegalArgumentException("Coin amount must be greater than 0")
                        }
                        
                        // Verify user exists
                        val user = adminRepository.getUserById(userId)
                        if (user == null) {
                            call.respondError(HttpStatusCode.NotFound, messageKey = MessageKeys.USER_NOT_FOUND)
                            return@post
                        }
                        
                        // Add coins using RewardService
                        val addCoinsRequest = AddCoinsRequest(
                            userId = userId,
                            amount = request.amount,
                            source = CoinSource.ADMIN_GRANT.name,
                            description = request.description ?: "Admin granted coins"
                        )
                        
                        val coin = rewardService.addCoins(addCoinsRequest)
                        
                        // Get updated user with new coin balance
                        val updatedUser = adminRepository.getUserById(userId)
                        call.respondApi(updatedUser, statusCode = HttpStatusCode.Created, messageKey = MessageKeys.COINS_ADDED)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.USERS_FAILED, message = "Failed to add coins: ${e.message}")
                    }
                }
            }
            
            // Reward Management (Read-only - rewards are system-generated, not admin-created)
            route("/rewards") {
                // Get all rewards (view only)
                get {
                    try {
                        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                        val rewards = adminRepository.getAllRewards(limit, offset)
                        call.respondApi(rewards, messageKey = MessageKeys.REWARDS_RETRIEVED)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.REWARDS_FAILED, message = "Failed to retrieve rewards: ${e.message}")
                    }
                }
                
                // Get reward by ID (view only)
                get("/{id}") {
                    try {
                        val id = call.parameters["id"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid reward ID")
                        val reward = adminRepository.getRewardById(id)
                        if (reward != null) {
                            call.respondApi(reward, messageKey = MessageKeys.REWARD_RETRIEVED)
                        } else {
                            call.respondError(HttpStatusCode.NotFound, messageKey = MessageKeys.REWARD_NOT_FOUND)
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.REWARDS_FAILED, message = "Failed to retrieve reward: ${e.message}")
                    }
                }
                
                // Note: Admins cannot create, update, or delete rewards
                // Rewards are system-generated only (from challenges, daily login, streaks, etc.)
            }
            
            // Consent Template Management
            route("/consents") {
                // Get all consent templates
                get {
                    try {
                        val templates = adminRepository.getAllConsentTemplates()
                        call.respondApi(templates, messageKey = MessageKeys.CONSENT_TEMPLATES_RETRIEVED)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.CONSENT_TEMPLATES_FAILED, message = "Failed to retrieve consent templates: ${e.message}")
                    }
                }
                
                // Get consent template by ID
                get("/{id}") {
                    try {
                        val id = call.parameters["id"]?.toIntOrNull()
                            ?: throw IllegalArgumentException("Invalid consent template ID")
                        val template = adminRepository.getConsentTemplateById(id)
                        if (template != null) {
                            call.respondApi(template, messageKey = MessageKeys.CONSENT_TEMPLATE_RETRIEVED)
                        } else {
                            call.respondError(HttpStatusCode.NotFound, messageKey = MessageKeys.CONSENT_TEMPLATE_NOT_FOUND)
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.CONSENT_TEMPLATES_FAILED, message = "Failed to retrieve consent template: ${e.message}")
                    }
                }
                
                // Create consent template
                post {
                    try {
                        val request = call.receive<CreateConsentTemplateRequest>()
                        val id = adminRepository.createConsentTemplate(request)
                        val template = adminRepository.getConsentTemplateById(id)
                        call.respondApi(template, statusCode = HttpStatusCode.Created, messageKey = MessageKeys.CONSENT_TEMPLATE_CREATED)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.CONSENT_TEMPLATES_FAILED, message = "Failed to create consent template: ${e.message}")
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
                            call.respondApi(template, messageKey = MessageKeys.CONSENT_TEMPLATE_UPDATED)
                        } else {
                            call.respondError(HttpStatusCode.NotFound, messageKey = MessageKeys.CONSENT_TEMPLATE_NOT_FOUND)
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.CONSENT_TEMPLATES_FAILED, message = "Failed to update consent template: ${e.message}")
                    }
                }
                
                // Delete consent template
                delete("/{id}") {
                    try {
                        val id = call.parameters["id"]?.toIntOrNull()
                            ?: throw IllegalArgumentException("Invalid consent template ID")
                        val deleted = adminRepository.deleteConsentTemplate(id)
                        if (deleted) {
                            call.respondApi("", messageKey = MessageKeys.CONSENT_TEMPLATE_DELETED)
                        } else {
                            call.respondError(HttpStatusCode.NotFound, messageKey = MessageKeys.CONSENT_TEMPLATE_NOT_FOUND)
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.CONSENT_TEMPLATES_FAILED, message = "Failed to delete consent template: ${e.message}")
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
                        call.respondApi(catalogItems, messageKey = MessageKeys.CATALOG_RETRIEVED)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.CATALOG_FAILED, message = "Failed to retrieve catalog: ${e.message}")
                    }
                }
                
                // Get catalog item by ID
                get("/{id}") {
                    try {
                        val id = call.parameters["id"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid catalog ID")
                        val catalogItem = rewardService.getRewardCatalogById(id)
                            ?: throw IllegalArgumentException("Catalog item not found")
                        call.respondApi(catalogItem, messageKey = MessageKeys.CATALOG_ITEM_RETRIEVED)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.CATALOG_FAILED, message = "Failed to retrieve catalog item: ${e.message}")
                    }
                }
                
                // Create catalog item
                post {
                    try {
                        val request = call.receive<com.apptime.code.rewards.CreateRewardCatalogRequest>()
                        val catalogItem = rewardService.createRewardCatalogItem(request)
                        call.respondApi(catalogItem, statusCode = HttpStatusCode.Created, messageKey = MessageKeys.CATALOG_ITEM_CREATED)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.CATALOG_FAILED, message = "Failed to create catalog item: ${e.message}")
                    }
                }
                
                // Update catalog item
                put("/{id}") {
                    try {
                        val id = call.parameters["id"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid catalog ID")
                        val request = call.receive<com.apptime.code.rewards.CreateRewardCatalogRequest>()
                        val catalogItem = rewardService.updateRewardCatalogItem(id, request)
                        call.respondApi(catalogItem, messageKey = MessageKeys.CATALOG_ITEM_UPDATED)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.CATALOG_FAILED, message = "Failed to update catalog item: ${e.message}")
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
                        call.respondApi(transactions, messageKey = MessageKeys.TRANSACTIONS_RETRIEVED)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.TRANSACTIONS_FAILED, message = "Failed to retrieve transactions: ${e.message}")
                    }
                }
                
                // Get transaction by ID
                get("/{id}") {
                    try {
                        val id = call.parameters["id"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid transaction ID")
                        val transaction = rewardService.getTransactionById(id)
                            ?: throw IllegalArgumentException("Transaction not found")
                        call.respondApi(transaction, messageKey = MessageKeys.TRANSACTION_RETRIEVED)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.TRANSACTIONS_FAILED, message = "Failed to retrieve transaction: ${e.message}")
                    }
                }
                
                // Update transaction status
                put("/{id}/status") {
                    try {
                        val id = call.parameters["id"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid transaction ID")
                        val request = call.receive<com.apptime.code.rewards.UpdateTransactionStatusRequest>()
                        val transaction = rewardService.updateTransactionStatus(id, request)
                        call.respondApi(transaction, messageKey = MessageKeys.TRANSACTION_STATUS_UPDATED)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.TRANSACTIONS_FAILED, message = "Failed to update transaction status: ${e.message}")
                    }
                }
            }
            
            // Asset Management
            route("/assets") {
                // Get list of all assets
                get {
                    try {
                        val assetDir = File("src/main/resources/asset")
                        val assets = if (assetDir.exists() && assetDir.isDirectory) {
                            assetDir.listFiles { _, name -> 
                                name.lowercase().endsWith(".png") || 
                                name.lowercase().endsWith(".jpg") || 
                                name.lowercase().endsWith(".jpeg") || 
                                name.lowercase().endsWith(".gif") || 
                                name.lowercase().endsWith(".webp")
                            }?.map { file ->
                                AssetInfo(
                                    name = file.name,
                                    size = file.length(),
                                    url = "/asset/${file.name}"
                                )
                            } ?: emptyList()
                        } else {
                            emptyList()
                        }
                        call.respondApi(assets, messageKey = MessageKeys.ASSETS_RETRIEVED)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.ASSETS_FAILED, message = "Failed to retrieve assets: ${e.message}")
                    }
                }
                
                // Upload asset
                post {
                    try {
                        val multipart = call.receiveMultipart()
                        var fileName: String? = null
                        var fileBytes: ByteArray? = null
                        
                        multipart.forEachPart { part ->
                            when (part) {
                                is PartData.FileItem -> {
                                    fileName = part.originalFileName
                                    fileBytes = part.streamProvider().readBytes()
                                }
                                else -> part.dispose()
                            }
                        }
                        
                        if (fileName == null || fileBytes == null) {
                            call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = "No file provided")
                            return@post
                        }
                        
                        // Validate file extension
                        val allowedExtensions = listOf(".png", ".jpg", ".jpeg", ".gif", ".webp")
                        val fileExtension = fileName!!.substringAfterLast('.', "").lowercase()
                        if (!allowedExtensions.contains(".$fileExtension")) {
                            call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = "Invalid file type. Allowed: ${allowedExtensions.joinToString(", ")}")
                            return@post
                        }
                        
                        // Ensure asset directory exists
                        val assetDir = File("src/main/resources/asset")
                        if (!assetDir.exists()) {
                            assetDir.mkdirs()
                        }
                        
                        // Save file
                        val targetFile = File(assetDir, fileName!!)
                        targetFile.writeBytes(fileBytes!!)
                        
                        val assetInfo = AssetInfo(
                            name = fileName!!,
                            size = fileBytes!!.size.toLong(),
                            url = "/asset/${fileName!!}"
                        )
                        
                        call.respondApi(assetInfo, statusCode = HttpStatusCode.Created, messageKey = MessageKeys.ASSET_UPLOADED)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.ASSETS_FAILED, message = "Failed to upload asset: ${e.message}")
                    }
                }
                
                // Delete asset
                delete("/{fileName}") {
                    try {
                        val fileName = call.parameters["fileName"]
                            ?: throw IllegalArgumentException("Invalid file name")
                        
                        // Security: prevent path traversal
                        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                            throw IllegalArgumentException("Invalid file name")
                        }
                        
                        val assetDir = File("src/main/resources/asset")
                        val targetFile = File(assetDir, fileName)
                        
                        if (!targetFile.exists()) {
                            call.respondError(HttpStatusCode.NotFound, messageKey = MessageKeys.ASSET_NOT_FOUND)
                            return@delete
                        }
                        
                        if (targetFile.delete()) {
                            call.respondApi("", messageKey = MessageKeys.ASSET_DELETED)
                        } else {
                            call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.ASSETS_FAILED, message = "Failed to delete asset")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.ASSETS_FAILED, message = "Failed to delete asset: ${e.message}")
                    }
                }
            }
            
            // Notification Management
            route("/notifications") {
                /**
                 * POST /api/admin/notifications/send
                 * Send notification to a specific user or broadcast to all users
                 */
                post("/send") {
                    try {
                        val request = call.receive<AdminSendNotificationRequest>()
                        
                        // Validate request
                        if (request.title.isBlank() || request.text.isBlank()) {
                            call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = "Title and text are required")
                            return@post
                        }
                        
                        if (!request.sendToAll && request.userId.isNullOrBlank()) {
                            call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = "Either userId or sendToAll must be specified")
                            return@post
                        }
                        
                        var sentCount = 0
                        var failedCount = 0
                        
                        if (request.sendToAll) {
                            // Broadcast to all users
                            try {
                                // Get all users (in batches to avoid memory issues)
                                var offset = 0
                                val batchSize = 100
                                var hasMore = true
                                
                                while (hasMore) {
                                    val users = adminRepository.getAllUsers(batchSize, offset)
                                    if (users.isEmpty()) {
                                        hasMore = false
                                        break
                                    }
                                    
                                    for (user in users) {
                                        try {
                                            notificationService.createAndSendNotification(
                                                userId = user.userId,
                                                title = request.title,
                                                text = request.text,
                                                type = request.type ?: "announcement",
                                                image = request.image,
                                                deeplink = request.deeplink,
                                                sendPush = true
                                            )
                                            sentCount++
                                        } catch (e: Exception) {
                                            failedCount++
                                            // Continue with other users even if one fails
                                        }
                                    }
                                    
                                    offset += batchSize
                                    if (users.size < batchSize) {
                                        hasMore = false
                                    }
                                }
                                
                                call.respondApi(
                                    AdminNotificationResponse(
                                        success = true,
                                        message = "Broadcast notification sent to $sentCount users ($failedCount failed)",
                                        sentCount = sentCount,
                                        failedCount = failedCount
                                    ),
                                    messageKey = MessageKeys.NOTIFICATION_SENT
                                )
                            } catch (e: Exception) {
                                call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.NOTIFICATION_FAILED, message = "Failed to broadcast notification: ${e.message}")
                            }
                        } else {
                            // Send to specific user
                            val user = adminRepository.getUserById(request.userId!!)
                            if (user == null) {
                                call.respondError(HttpStatusCode.NotFound, messageKey = MessageKeys.USER_NOT_FOUND)
                                return@post
                            }
                            
                            try {
                                notificationService.createAndSendNotification(
                                    userId = request.userId,
                                    title = request.title,
                                    text = request.text,
                                    type = request.type ?: "general",
                                    image = request.image,
                                    deeplink = request.deeplink,
                                    sendPush = true
                                )
                                
                                call.respondApi(
                                    AdminNotificationResponse(
                                        success = true,
                                        message = "Notification sent successfully to user ${request.userId}",
                                        sentCount = 1,
                                        failedCount = 0
                                    ),
                                    messageKey = MessageKeys.NOTIFICATION_SENT
                                )
                            } catch (e: Exception) {
                                call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.NOTIFICATION_FAILED, message = "Failed to send notification: ${e.message}")
                            }
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.NOTIFICATION_FAILED, message = "Failed to send notification: ${e.message}")
                    }
                }
            }
            
            // Feedback Management
            route("/feedback") {
                /**
                 * GET /api/admin/feedback
                 * Get all feedback with optional filters
                 * Query params: status, category, limit, offset
                 */
                get {
                    try {
                        val status = call.request.queryParameters["status"]
                        val category = call.request.queryParameters["category"]
                        val limit = call.request.queryParameters["limit"]?.toInt()
                        val offset = call.request.queryParameters["offset"]?.toInt() ?: 0
                        
                        val response = feedbackService.getAllFeedback(status, category, limit, offset)
                        call.respondApi(response, messageKey = MessageKeys.FEEDBACK_RETRIEVED)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.FEEDBACK_RETRIEVAL_FAILED, message = "Failed to retrieve feedback: ${e.message}")
                    }
                }
                
                /**
                 * GET /api/admin/feedback/{feedbackId}
                 * Get feedback by ID
                 */
                get("/{feedbackId}") {
                    try {
                        val feedbackId = call.parameters["feedbackId"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid feedback ID")
                        
                        val feedback = feedbackService.getFeedbackById(feedbackId)
                        if (feedback != null) {
                            call.respondApi(feedback, messageKey = MessageKeys.FEEDBACK_RETRIEVED)
                        } else {
                            call.respondError(HttpStatusCode.NotFound, messageKey = MessageKeys.FEEDBACK_NOT_FOUND, message = "Feedback not found")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.FEEDBACK_RETRIEVAL_FAILED, message = "Failed to retrieve feedback: ${e.message}")
                    }
                }
                
                /**
                 * PUT /api/admin/feedback/{feedbackId}/status
                 * Update feedback status
                 */
                put("/{feedbackId}/status") {
                    try {
                        val feedbackId = call.parameters["feedbackId"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid feedback ID")
                        
                        val request = call.receive<UpdateFeedbackStatusRequest>()
                        val response = feedbackService.updateFeedbackStatus(feedbackId, request)
                        call.respondApi(response, messageKey = MessageKeys.FEEDBACK_UPDATED)
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.FEEDBACK_UPDATE_FAILED, message = "Failed to update feedback: ${e.message}")
                    }
                }
                
                /**
                 * DELETE /api/admin/feedback/{feedbackId}
                 * Delete feedback
                 */
                delete("/{feedbackId}") {
                    try {
                        val feedbackId = call.parameters["feedbackId"]?.toLongOrNull()
                            ?: throw IllegalArgumentException("Invalid feedback ID")
                        
                        val success = feedbackService.deleteFeedback(feedbackId)
                        if (success) {
                            call.respondApi(
                                mapOf(
                                    "message" to "Feedback deleted successfully",
                                    "feedbackId" to feedbackId
                                ),
                                messageKey = MessageKeys.FEEDBACK_DELETED
                            )
                        } else {
                            call.respondError(HttpStatusCode.NotFound, messageKey = MessageKeys.FEEDBACK_NOT_FOUND, message = "Feedback not found")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, messageKey = MessageKeys.INVALID_REQUEST, message = e.message)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.FEEDBACK_DELETE_FAILED, message = "Failed to delete feedback: ${e.message}")
                    }
                }
                
                /**
                 * GET /api/admin/feedback/stats
                 * Get feedback statistics
                 */
                get("/stats") {
                    try {
                        val totalCount = feedbackRepository.getFeedbackCount()
                        val pendingCount = feedbackRepository.getFeedbackCount(status = "pending")
                        val reviewedCount = feedbackRepository.getFeedbackCount(status = "reviewed")
                        val resolvedCount = feedbackRepository.getFeedbackCount(status = "resolved")
                        val closedCount = feedbackRepository.getFeedbackCount(status = "closed")
                        
                        val stats = mapOf(
                            "total" to totalCount,
                            "pending" to pendingCount,
                            "reviewed" to reviewedCount,
                            "resolved" to resolvedCount,
                            "closed" to closedCount
                        )
                        
                        call.respondApi(stats, messageKey = MessageKeys.FEEDBACK_RETRIEVED)
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, messageKey = MessageKeys.FEEDBACK_RETRIEVAL_FAILED, message = "Failed to retrieve feedback stats: ${e.message}")
                    }
                }
            }
        }
    }
}
