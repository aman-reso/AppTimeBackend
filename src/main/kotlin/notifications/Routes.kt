package com.apptime.code.notifications

import com.apptime.code.common.requireUserId
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import users.UserRepository

@Serializable
data class MarkAsReadRequest(
    val notificationId: Long
)

@Serializable
data class NotificationResponse(
    val message: String,
    val notification: NotificationData? = null
)

/**
 * Configure notification routes
 */
fun Application.configureNotificationRoutes() {
    val repository = NotificationRepository()
    val userRepository = UserRepository()
    val service = NotificationService(repository, userRepository)
    
    routing {
        route("/api/notifications") {
            authenticate("auth-bearer") {
                /**
                 * Get user notifications
                 * GET /api/notifications?isRead=true&limit=20&offset=0
                 */
                get {
                    val userId = call.requireUserId()
                val isRead = call.request.queryParameters["isRead"]?.toBoolean()
                val limit = call.request.queryParameters["limit"]?.toInt()
                val offset = call.request.queryParameters["offset"]?.toInt() ?: 0
                
                val response = service.getUserNotifications(userId, isRead, limit, offset)
                call.respond(response)
            }
            
                /**
                 * Get unread notification count
                 * GET /api/notifications/unread-count
                 */
                get("/unread-count") {
                    val userId = call.requireUserId()
                val unreadCount = repository.getUnreadCount(userId)
                call.respond(mapOf(
                    "userId" to userId,
                    "unreadCount" to unreadCount
                ))
            }
            
                /**
                 * Mark notification as read
                 * POST /api/notifications/read
                 */
                post("/read") {
                    val userId = call.requireUserId()
                val request = call.receive<MarkAsReadRequest>()
                
                val success = service.markAsRead(request.notificationId, userId)
                if (success) {
                    val notification = service.getNotificationById(request.notificationId, userId)
                    call.respond(NotificationResponse(
                        message = "Notification marked as read",
                        notification = notification
                    ))
                } else {
                    call.respond(
                        status = io.ktor.http.HttpStatusCode.NotFound,
                        NotificationResponse(message = "Notification not found")
                    )
                }
            }
            
                /**
                 * Mark all notifications as read
                 * POST /api/notifications/read-all
                 */
                post("/read-all") {
                    val userId = call.requireUserId()
                val count = service.markAllAsRead(userId)
                call.respond(mapOf(
                    "message" to "Marked $count notification(s) as read",
                    "count" to count
                ))
            }
            
                /**
                 * Delete a notification
                 * DELETE /api/notifications/{notificationId}
                 */
                delete("/{notificationId}") {
                    val userId = call.requireUserId()
                val notificationId = call.parameters["notificationId"]?.toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid notification ID")
                
                val success = service.deleteNotification(notificationId, userId)
                if (success) {
                    call.respond(mapOf(
                        "message" to "Notification deleted successfully",
                        "notificationId" to notificationId
                    ))
                } else {
                    call.respond(
                        status = io.ktor.http.HttpStatusCode.NotFound,
                        mapOf("message" to "Notification not found")
                    )
                }
            }
            
                /**
                 * Get notification by ID
                 * GET /api/notifications/{notificationId}
                 */
                get("/{notificationId}") {
                    val userId = call.requireUserId()
                val notificationId = call.parameters["notificationId"]?.toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid notification ID")
                
                val notification = service.getNotificationById(notificationId, userId)
                if (notification != null) {
                    call.respond(notification)
                } else {
                    call.respond(
                        status = io.ktor.http.HttpStatusCode.NotFound,
                        mapOf("message" to "Notification not found")
                    )
                }
            }
            }
        }
    }
}

