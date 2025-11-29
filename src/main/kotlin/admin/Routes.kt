package com.apptime.code.admin

import com.apptime.code.common.respondApi
import com.apptime.code.common.respondError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*

/**
 * Configure admin-related routes
 */
fun Application.configureAdminRoutes() {
    val statsRepository = StatsRepository()
    val statsService = AdminService(statsRepository)
    val adminRepository = AdminRepository()
    
    routing {
        route("/api/admin") {
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
        }
    }
}
