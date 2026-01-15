package users

import com.apptime.code.common.requireUserId
import com.apptime.code.common.respondApi
import com.apptime.code.common.respondError
import com.apptime.code.notifications.FirebaseNotificationService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Configure user-related routes
 */
fun Application.configureUserRoutes() {
    val repository = UserRepository()
    val service = UserService(repository)

    routing {
        route("/api/users") {
            /**
             * POST /api/users/register
             * Register a new device/user
             */
            post("/register") {
                try {
                    val request = call.receive<DeviceRegistrationRequest>()
                    val response = service.registerDevice(request)
                    call.respondApi(response, "Device registered successfully", HttpStatusCode.Created)
                } catch (e: IllegalArgumentException) {
                    call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.InternalServerError, "Registration failed: ${e.message}")
                }
            }
            
            /**
             * GET /api/users/search
             * Search users by name (requires authentication)
             * Query param: q (required) - search query (minimum 2 characters)
             * Returns up to 10 matching users
             */
            authenticate("auth-bearer") {
            get("/search") {
                try {
                        val userId = call.requireUserId()
                    val query = call.request.queryParameters["q"]
                        ?: throw IllegalArgumentException("Search query parameter 'q' is required")
                    
                    val results = service.searchUsers(query)
                    call.respondApi(results, "Users found: ${results.size}")
                } catch (e: IllegalArgumentException) {
                    call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.InternalServerError, "Search failed: ${e.message}")
                    }
                }
            }
            
            /**
             * GET /api/users/{username}/totp/generate
             * Generate TOTP code for a user by username (public endpoint, no auth required)
             * Returns 6-digit TOTP code (secret is never exposed)
             * User generates TOTP on their app and can share it
             */
            get("/{username}/totp/generate") {
                try {
                    val username = call.parameters["username"]
                        ?: throw IllegalArgumentException("Username is required")
                    
                    val response = service.generateTOTPCodeByUsername(username)
                    call.respondApi(response, "TOTP code generated successfully")
                } catch (e: IllegalArgumentException) {
                    call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.InternalServerError, "Failed to generate TOTP code: ${e.message}")
                }
            }
            
            /**
             * POST /api/users/{username}/totp/verify
             * Verify TOTP code for a user by username
             * Requires authentication - requestingUserId is taken from authenticated user
             * Creates a verification session valid for 1 hour
             */
            authenticate("auth-bearer") {
            post("/{username}/totp/verify") {
                try {
                        val requestingUserId = call.requireUserId() // User A (authenticated)
                        val username = call.parameters["username"] // User B's username
                        ?: throw IllegalArgumentException("Username is required")
                    
                    val request = call.receive<TOTPVerifyRequest>()
                        val response = service.verifyTOTPCodeByUsername(
                            username = username,
                            code = request.code,
                            requestingUserId = requestingUserId,
                            durationSeconds = request.durationSeconds
                        )
                    
                    if (response.valid) {
                        call.respondApi(response, "TOTP code verified successfully")
                    } else {
                        call.respondError(HttpStatusCode.BadRequest, response.message)
                    }
                } catch (e: IllegalArgumentException) {
                    call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.InternalServerError, "Failed to verify TOTP code: ${e.message}")
                    }
                }
            }
            
            /**
             * GET /api/users/{username}/totp/status
             * Check if TOTP is verified and access is still valid
             * Requires authentication
             * Returns access status with remaining time
             */
            authenticate("auth-bearer") {
            get("/{username}/totp/status") {
                try {
                    val requestingUserId = call.requireUserId() // User A (authenticated)
                    val username = call.parameters["username"] // User B's username
                        ?: throw IllegalArgumentException("Username is required")
                    
                    val response = service.checkTOTPAccessStatus(username, requestingUserId)
                    
                    if (response.hasAccess) {
                        call.respondApi(response, "Access status retrieved successfully")
                    } else {
                        call.respondApi(response, response.message, HttpStatusCode.Forbidden)
                    }
                } catch (e: IllegalArgumentException) {
                    call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                } catch (e: Exception) {
                    call.respondError(HttpStatusCode.InternalServerError, "Failed to check access status: ${e.message}")
                }
            }
            }
            
            /**
             * POST /api/users/{username}/profile
             * Get user profile after TOTP verification
             * Requires authentication and a valid verification session
             * Returns simple success or failure response
             */
            authenticate("auth-bearer") {
            post("/{username}/profile") {
                try {
                        val requestingUserId = call.requireUserId() // User A (authenticated)
                        val username = call.parameters["username"] // User B's username
                        ?: throw IllegalArgumentException("Username is required")
                    
                        // Check if requesting user has valid access session
                        val targetUserId = repository.getUserIdByUsername(username)
                            ?: throw IllegalArgumentException("User not found")
                        
                        if (!service.hasAccessToUserData(requestingUserId, targetUserId)) {
                            call.respondApi(
                                data = SimpleResponse(
                                    success = false,
                                    message = "No valid access session. Please verify TOTP code first."
                                ),
                                statusCode = HttpStatusCode.Forbidden
                            )
                            return@post
                        }
                        
                        // Get and return profile
                        val profile = repository.getUserProfileByUsername(username)
                            ?: throw IllegalArgumentException("User not found")
                        
                        val publicProfile = PublicUserProfile(
                            username = profile.username,
                            email = profile.email,
                            name = profile.name,
                            createdAt = profile.createdAt,
                            updatedAt = profile.updatedAt,
                            lastSyncTime = profile.lastSyncTime
                        )

                        // Return success response with profile data
                        call.respondApi(
                            data = publicProfile,
                            statusCode = HttpStatusCode.OK
                        )
                } catch (e: IllegalArgumentException) {
                        // Return simple failure response
                        call.respondApi(
                            data = SimpleResponse(success = false, message = e.message ?: "Invalid request"),
                            statusCode = HttpStatusCode.BadRequest
                        )
                } catch (e: Exception) {
                        // Return simple failure response
                        call.respondApi(
                            data = SimpleResponse(success = false, message = e.message ?: "Failed to get profile"),
                            statusCode = HttpStatusCode.InternalServerError
                        )
                    }
                }
            }
        }

        route("/api/v1/user") {
            /**
             * GET /api/v1/user/profile
             * Get authenticated user's profile (requires authentication)
             */
            authenticate("auth-bearer") {
                get("/profile") {
                    try {
                        val userId = call.requireUserId()
                        val profile = service.getUserProfile(userId)

                        if (profile != null) {
                            call.respondApi(profile, "Profile retrieved successfully")
                        } else {
                            call.respondError(HttpStatusCode.NotFound, "User not found")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(
                            HttpStatusCode.InternalServerError, "Failed to retrieve profile: ${e.message}"
                        )
                    }
                }

                /**
                 * PUT /api/v1/user/profile
                 * Update authenticated user's profile - username and firebaseToken can be updated (requires authentication)
                 */
                put("/profile") {
                    try {
                        val userId = call.requireUserId()
                        val request = call.receive<UpdateProfileRequest>()

                        // At least one field must be provided
                        if (request.username.isNullOrBlank() && request.firebaseToken.isNullOrBlank()) {
                            call.respondError(HttpStatusCode.BadRequest, "At least one field (username or firebaseToken) is required for update")
                            return@put
                        }

                        // Validate username if provided
                        if (!request.username.isNullOrBlank()) {
                            // Validate username length
                            if (request.username!!.length < 3) {
                                call.respondError(HttpStatusCode.BadRequest, "Username must be at least 3 characters long")
                                return@put
                            }

                            if (request.username!!.length > 30) {
                                call.respondError(HttpStatusCode.BadRequest, "Username must not exceed 30 characters")
                                return@put
                            }

                            // Validate username format: only alphanumeric characters, underscores, and hyphens
                            val usernamePattern = Regex("^[a-zA-Z0-9_-]+$")
                            if (!usernamePattern.matches(request.username!!)) {
                                call.respondError(HttpStatusCode.BadRequest, "Username can only contain letters, numbers, underscores, and hyphens")
                                return@put
                            }

                            // Validate that username contains at least one letter
                            val hasLetter = Regex("[a-zA-Z]").containsMatchIn(request.username!!)
                            if (!hasLetter) {
                                call.respondError(HttpStatusCode.BadRequest, "Username must contain at least one letter")
                                return@put
                            }
                        }

                        val updatedProfile = service.updateUserProfile(userId, request.username, request.firebaseToken)

                        if (updatedProfile != null) {
                            call.respondApi(updatedProfile, "Profile updated successfully")
                        } else {
                            call.respondError(HttpStatusCode.NotFound, "User not found")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to update profile: ${e.message}")
                    }
                }
                
                /**
                 * POST /api/v1/user/username
                 * Change username for authenticated user (requires authentication)
                 * Username must be unique
                 */
                post("/username") {
                    try {
                        val userId = call.requireUserId()
                        val request = call.receive<ChangeUsernameRequest>()

                        if (request.username.isBlank()) {
                            call.respondError(HttpStatusCode.BadRequest, "Username is required")
                            return@post
                        }

                        // Validate username length
                        if (request.username.length < 3) {
                            call.respondError(HttpStatusCode.BadRequest, "Username must be at least 3 characters long")
                            return@post
                        }

                        if (request.username.length > 30) {
                            call.respondError(HttpStatusCode.BadRequest, "Username must not exceed 30 characters")
                            return@post
                        }

                        // Validate username format: only alphanumeric characters, underscores, and hyphens
                        val usernamePattern = Regex("^[a-zA-Z0-9_-]+$")
                        if (!usernamePattern.matches(request.username)) {
                            call.respondError(HttpStatusCode.BadRequest, "Username can only contain letters, numbers, underscores, and hyphens")
                            return@post
                        }

                        // Validate that username contains at least one letter
                        val hasLetter = Regex("[a-zA-Z]").containsMatchIn(request.username)
                        if (!hasLetter) {
                            call.respondError(HttpStatusCode.BadRequest, "Username must contain at least one letter")
                            return@post
                        }

                        val updatedProfile = service.updateUserProfile(userId, request.username)

                        if (updatedProfile != null) {
                            call.respondApi(updatedProfile, "Username updated successfully")
                        } else {
                            call.respondError(HttpStatusCode.NotFound, "User not found")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to update username: ${e.message}")
                    }
                }
                
                /**
                 * GET /api/v1/user/totp/generate
                 * Generate TOTP code for authenticated user (requires authentication)
                 * Returns 6-digit TOTP code with validity information
                 */
                get("/totp/generate") {
                    try {
                        val userId = call.requireUserId()
                        val response = service.generateTOTPCodeByUserId(userId)
                        call.respondApi(response, "TOTP code generated successfully")
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to generate TOTP code: ${e.message}")
                    }
                }
                
                /**
                 * GET /api/v1/user/totp/sessions
                 * Get all active TOTP verification sessions for authenticated user (debug endpoint)
                 * Shows which users this user has verified access to
                 */
                get("/totp/sessions") {
                    try {
                        val requestingUserId = call.requireUserId()
                        val sessions = repository.getActiveSessionsForUser(requestingUserId)
                        call.respondApi(sessions, "Active TOTP verification sessions retrieved successfully")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to get sessions: ${e.message}")
                    }
                }
                
                /**
                 * GET /api/v1/user/sync/status
                 * Get sync status for authenticated user
                 * Returns last sync time and current server time for comparison
                 */
                get("/sync/status") {
                    try {
                        val userId = call.requireUserId()
                        val lastSyncTime = repository.getLastSyncTime(userId)
                        val currentServerTime = System.currentTimeMillis()
                        
                        val timeSinceLastSync = lastSyncTime?.let { currentServerTime.toLong() - it }
                        val timeSinceLastSyncSeconds = timeSinceLastSync?.let { (it / 1000).toInt() }
                        val timeSinceLastSyncMinutes = timeSinceLastSync?.let { (it / 60000).toInt() }
                        
                        val syncStatus = mapOf(
                            "userId" to userId,
                            "lastSyncTime" to (lastSyncTime ?: 0L),
                            "lastSyncTimeISO" to (lastSyncTime?.let { 
                                kotlinx.datetime.Instant.fromEpochMilliseconds(it).toString() 
                            } ?: null),
                            "currentServerTime" to currentServerTime,
                            "currentServerTimeISO" to kotlinx.datetime.Clock.System.now().toString(),
                            "timeSinceLastSync" to (timeSinceLastSync ?: null),
                            "timeSinceLastSyncSeconds" to (timeSinceLastSyncSeconds ?: null),
                            "timeSinceLastSyncMinutes" to (timeSinceLastSyncMinutes ?: null),
                            "hasSyncedBefore" to (lastSyncTime != null)
                        )
                        
                        call.respondApi(syncStatus, "Sync status retrieved successfully")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to get sync status: ${e.message}")
                    }
                }
                
                /**
                 * GET /api/v1/user/totp/control-panel
                 * Get TOTP control panel overview
                 * Shows all users who have access to authenticated user's data via TOTP sessions
                 */
                get("/totp/control-panel") {
                    try {
                        val userId = call.requireUserId()
                        val response = service.getControlPanelOverview(userId)
                        call.respondApi(response, "Control panel data retrieved successfully")
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to get control panel data: ${e.message}")
                    }
                }
                
                /**
                 * POST /api/v1/user/totp/grant-access
                 * Grant access to a user without requiring TOTP verification
                 */
                post("/totp/grant-access") {
                    try {
                        val userId = call.requireUserId()
                        val request = call.receive<GrantAccessRequest>()
                        
                        if (request.username.isBlank()) {
                            call.respondError(HttpStatusCode.BadRequest, "Username is required")
                            return@post
                        }
                        
                        // Get target user info for notification
                        val requestingUserId = repository.getUserIdByUsername(request.username)
                            ?: throw IllegalArgumentException("User not found")
                        
                        val targetUser = repository.getUserById(userId)
                        val targetUsername = targetUser?.username ?: "User"
                        
                        val response = service.grantAccessWithoutTOTP(
                            userId,
                            request.username,
                            request.durationSeconds
                        )
                        
                        if (response.success) {
                            // Send notification to the user who got access
                            val requestingUser = repository.getUserById(requestingUserId)
                            val firebaseToken = requestingUser?.firebaseToken
                            
                            if (!firebaseToken.isNullOrBlank()) {
                                val durationText = when {
                                    response.remainingSeconds!! < 3600 -> "${response.remainingSeconds / 60} minute(s)"
                                    response.remainingSeconds < 86400 -> "${response.remainingSeconds / 3600} hour(s)"
                                    response.remainingSeconds < 31536000 -> "${response.remainingSeconds / 86400} day(s)"
                                    else -> "${response.remainingSeconds / 31536000} year(s)"
                                }
                                
                                val title = "Access Granted"
                                val body = "$targetUsername has granted you access. Duration: $durationText"
                                
                                GlobalScope.launch {
                                    FirebaseNotificationService.sendNotification(
                                        firebaseToken = firebaseToken,
                                        title = title,
                                        body = body,
                                        data = mapOf(
                                            "type" to "access_granted",
                                            "targetUserId" to userId,
                                            "targetUsername" to targetUsername,
                                            "expiresAt" to (response.expiresAt ?: ""),
                                            "remainingSeconds" to (response.remainingSeconds?.toString() ?: "")
                                        )
                                    )
                                }
                            }
                            
                            call.respondApi(response, "Access granted successfully")
                        } else {
                            call.respondError(HttpStatusCode.BadRequest, response.message)
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to grant access: ${e.message}")
                    }
                }
                
                /**
                 * POST /api/v1/user/totp/revoke-access
                 * Revoke access for a user (invalidate their TOTP session)
                 */
                post("/totp/revoke-access") {
                    try {
                        val userId = call.requireUserId()
                        val request = call.receive<RevokeAccessRequest>()
                        
                        if (request.username.isBlank()) {
                            call.respondError(HttpStatusCode.BadRequest, "Username is required")
                            return@post
                        }

                        // Get user ID by username
                        val requestingUserId = repository.getUserIdByUsername(request.username)
                            ?: throw IllegalArgumentException("User not found")

                        val targetUser = repository.getUserById(userId)
                        val targetUsername = targetUser?.username ?: "User"

                        val success = service.revokeAccess(requestingUserId, userId)
                        
                        if (success) {
                            // Send notification to the user whose access was revoked
                            val requestingUser = repository.getUserById(requestingUserId)
                            val firebaseToken = requestingUser?.firebaseToken
                            
                            if (!firebaseToken.isNullOrBlank()) {
                                val title = "Access Revoked"
                                val body = "$targetUsername has revoked your access."
                                
                                GlobalScope.launch {
                                    FirebaseNotificationService.sendNotification(
                                        firebaseToken = firebaseToken,
                                        title = title,
                                        body = body,
                                        data = mapOf(
                                            "type" to "access_revoked",
                                            "targetUserId" to userId,
                                            "targetUsername" to targetUsername
                                        )
                                    )
                                }
                            }
                            
                            call.respondApi(
                                SimpleResponse(success = true, message = "Access revoked successfully"),
                                "Access revoked successfully"
                            )
                        } else {
                            call.respondError(HttpStatusCode.NotFound, "No active session found to revoke")
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to revoke access: ${e.message}")
                    }
                }
                
                /**
                 * POST /api/v1/user/totp/extend-access
                 * Extend access time for a user's session
                 */
                post("/totp/extend-access") {
                    try {
                        val userId = call.requireUserId()
                        val request = call.receive<ExtendAccessRequest>()
                        
                        if (request.username.isBlank()) {
                            call.respondError(HttpStatusCode.BadRequest, "Requesting user ID is required")
                            return@post
                        }
                        
                        if (request.additionalSeconds <= 0) {
                            call.respondError(HttpStatusCode.BadRequest, "Additional seconds must be greater than 0")
                            return@post
                        }
                        
                        val requestingUserId = repository.getUserIdByUsername(request.username)
                            ?: throw IllegalArgumentException("User not found")
                        
                        val targetUser = repository.getUserById(userId)
                        val targetUsername = targetUser?.username ?: "User"
                        
                        val response = service.extendAccessTime(userId, requestingUserId, request.additionalSeconds)
                        
                        if (response.success) {
                            // Send notification to the user whose access was extended
                            val requestingUser = repository.getUserById(requestingUserId)
                            val firebaseToken = requestingUser?.firebaseToken
                            
                            if (!firebaseToken.isNullOrBlank()) {
                                val additionalTimeText = when {
                                    request.additionalSeconds < 3600 -> "${request.additionalSeconds / 60} minute(s)"
                                    request.additionalSeconds < 86400 -> "${request.additionalSeconds / 3600} hour(s)"
                                    request.additionalSeconds < 604800 -> "${request.additionalSeconds / 86400} day(s)"
                                    else -> "${request.additionalSeconds / 604800} week(s)"
                                }
                                
                                val remainingTimeText = response.remainingSeconds?.let {
                                    when {
                                        it < 3600 -> "${it / 60} minute(s)"
                                        it < 86400 -> "${it / 3600} hour(s)"
                                        it < 31536000 -> "${it / 86400} day(s)"
                                        else -> "${it / 31536000} year(s)"
                                    }
                                } ?: "Unknown"
                                
                                val title = "Access Extended"
                                val body = "$targetUsername has extended your access time. Additional time: $additionalTimeText. Total remaining: $remainingTimeText"
                                
                                GlobalScope.launch {
                                    FirebaseNotificationService.sendNotification(
                                        firebaseToken = firebaseToken,
                                        title = title,
                                        body = body,
                                        data = mapOf(
                                            "type" to "access_extended",
                                            "targetUserId" to userId,
                                            "targetUsername" to targetUsername,
                                            "additionalSeconds" to request.additionalSeconds.toString(),
                                            "newExpiresAt" to (response.expiresAt ?: ""),
                                            "remainingSeconds" to (response.remainingSeconds?.toString() ?: "")
                                        )
                                    )
                                }
                            }
                            
                            call.respondApi(response, "Access time extended successfully")
                        } else {
                            call.respondError(HttpStatusCode.NotFound, response.message)
                        }
                    } catch (e: IllegalArgumentException) {
                        call.respondError(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
                    } catch (e: Exception) {
                        call.respondError(HttpStatusCode.InternalServerError, "Failed to extend access time: ${e.message}")
                    }
                }
            }
        }
    }
}

