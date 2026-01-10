package users

import com.apptime.code.common.requireUserId
import com.apptime.code.common.respondApi
import com.apptime.code.common.respondError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

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
            }
        }
    }
}

