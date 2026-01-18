package com.apptime.code.notifications

import users.UserRepository
import java.util.logging.Logger

/**
 * Service for notification business logic
 */
class NotificationService(
    private val repository: NotificationRepository,
    private val userRepository: UserRepository
) {
    private val logger = Logger.getLogger(NotificationService::class.java.name)
    
    /**
     * Create and send a notification to a user
     * This creates a notification in the database and sends a push notification if Firebase token is available
     */
    suspend fun createAndSendNotification(
        userId: String,
        title: String,
        text: String,
        type: String? = null,
        image: String? = null,
        deeplink: String? = null,
        sendPush: Boolean = true
    ): NotificationData {
        // Create notification in database
        val notificationId = repository.createNotification(
            userId = userId,
            title = title,
            text = text,
            type = type,
            image = image,
            deeplink = deeplink
        )
        
        // Send push notification if enabled and user has Firebase token
        if (sendPush) {
            try {
                val user = userRepository.getUserById(userId)
                val firebaseToken = user?.firebaseToken
                
                if (!firebaseToken.isNullOrBlank()) {
                    val data = mutableMapOf<String, String>(
                        "type" to (type ?: "general"),
                        "notificationId" to notificationId.toString()
                    )
                    
                    if (deeplink != null) {
                        data["deeplink"] = deeplink
                    }
                    
                    FirebaseNotificationService.sendNotification(
                        firebaseToken = firebaseToken,
                        title = title,
                        body = text,
                        data = data
                    )
                } else {
                    logger.info("User $userId does not have a Firebase token. Notification saved but push not sent.")
                }
            } catch (e: Exception) {
                logger.warning("Failed to send push notification to user $userId: ${e.message}")
                // Continue even if push fails - notification is still saved in database
            }
        }
        
        return repository.getNotificationById(notificationId, userId)!!
    }
    
    /**
     * Get notifications for a user
     */
    suspend fun getUserNotifications(
        userId: String,
        isRead: Boolean? = null,
        limit: Int? = null,
        offset: Int = 0
    ): NotificationHistoryResponse {
        val notifications = repository.getUserNotifications(userId, isRead, limit, offset)
        val unreadCount = repository.getUnreadCount(userId)
        
        return NotificationHistoryResponse(
            notifications = notifications,
            totalCount = notifications.size,
            unreadCount = unreadCount
        )
    }
    
    /**
     * Mark notification as read
     */
    suspend fun markAsRead(notificationId: Long, userId: String): Boolean {
        return repository.markAsRead(notificationId, userId)
    }
    
    /**
     * Mark all notifications as read for a user
     */
    suspend fun markAllAsRead(userId: String): Int {
        return repository.markAllAsRead(userId)
    }
    
    /**
     * Delete a notification
     */
    suspend fun deleteNotification(notificationId: Long, userId: String): Boolean {
        return repository.deleteNotification(notificationId, userId)
    }
    
    /**
     * Get notification by ID
     */
    suspend fun getNotificationById(notificationId: Long, userId: String): NotificationData? {
        return repository.getNotificationById(notificationId, userId)
    }
    
    // ========== HELPER METHODS FOR COMMON NOTIFICATION TYPES ==========
    
    /**
     * Send challenge join notification
     */
    suspend fun sendChallengeJoinNotification(
        userId: String,
        challengeTitle: String,
        challengeId: Long
    ) {
        createAndSendNotification(
            userId = userId,
            title = "Challenge Joined! 🎯",
            text = "You've joined the challenge: $challengeTitle. Good luck!",
            type = "challenge_join",
            deeplink = "app://challenge/$challengeId"
        )
    }
    
    /**
     * Send challenge completion notification
     */
    suspend fun sendChallengeCompletionNotification(
        userId: String,
        challengeTitle: String,
        rank: Int?,
        challengeId: Long
    ) {
        val rankText = if (rank != null) {
            "You finished in rank #$rank!"
        } else {
            "You completed the challenge!"
        }
        
        createAndSendNotification(
            userId = userId,
            title = "Challenge Completed! 🏆",
            text = "$rankText Challenge: $challengeTitle",
            type = "challenge_complete",
            deeplink = "app://challenge/$challengeId"
        )
    }
    
    /**
     * Send reward notification
     */
    suspend fun sendRewardNotification(
        userId: String,
        rewardTitle: String,
        rewardDescription: String,
        rewardId: Long? = null
    ) {
        val deeplink = if (rewardId != null) {
            "app://reward/$rewardId"
        } else {
            "app://rewards"
        }
        
        createAndSendNotification(
            userId = userId,
            title = "New Reward! 🎁",
            text = "$rewardTitle: $rewardDescription",
            type = "reward",
            deeplink = deeplink
        )
    }
    
    /**
     * Send challenge reward notification
     */
    suspend fun sendChallengeRewardNotification(
        userId: String,
        challengeTitle: String,
        rank: Int,
        coins: Long,
        challengeId: Long
    ) {
        createAndSendNotification(
            userId = userId,
            title = "Challenge Reward! 🏅",
            text = "You won rank #$rank in '$challengeTitle'! You earned $coins coins.",
            type = "challenge_reward",
            deeplink = "app://challenge/$challengeId"
        )
    }
    
    /**
     * Send focus milestone notification
     */
    suspend fun sendFocusMilestoneNotification(
        userId: String,
        milestone: String,
        focusTime: Long
    ) {
        val hours = focusTime / (1000 * 60 * 60)
        createAndSendNotification(
            userId = userId,
            title = "Focus Milestone! 🎯",
            text = "Congratulations! You've reached $milestone with $hours hours of focus time.",
            type = "focus_milestone",
            deeplink = "app://focus"
        )
    }
    
    /**
     * Send daily limit reached notification
     */
    suspend fun sendDailyLimitNotification(
        userId: String,
        appName: String,
        limitMinutes: Int
    ) {
        createAndSendNotification(
            userId = userId,
            title = "Daily Limit Reached ⏰",
            text = "You've reached your daily limit of $limitMinutes minutes for $appName.",
            type = "daily_limit",
            deeplink = "app://usage"
        )
    }
    
    /**
     * Send break reminder notification
     */
    suspend fun sendBreakReminderNotification(
        userId: String,
        screenTimeMinutes: Int
    ) {
        createAndSendNotification(
            userId = userId,
            title = "Time for a Break! ☕",
            text = "You've been using your device for $screenTimeMinutes minutes. Take a break!",
            type = "break_reminder",
            deeplink = "app://home"
        )
    }
}

