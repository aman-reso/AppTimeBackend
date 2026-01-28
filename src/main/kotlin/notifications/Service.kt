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
            deeplink = "apptime://screen/challenge_detail/$challengeId"
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
            deeplink = "apptime://screen/challenge_detail/$challengeId"
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
            "apptime://screen/reward_transaction/$rewardId"
        } else {
            "rewards"
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
            deeplink = "apptime://screen/challenge_detail/$challengeId"
        )
    }
    
    /**
     * Send challenge winner notification to other users
     * Notifies all other participants that a user won the challenge and got coins
     */
    suspend fun sendChallengeWinnerNotificationToOthers(
        winnerUserId: String,
        winnerUsername: String,
        challengeTitle: String,
        coins: Long,
        challengeId: Long,
        otherUserIds: List<String>
    ) {
        // Get username for display (use provided username or fallback to userId)
        val displayName = if (winnerUsername.isNotBlank()) winnerUsername else winnerUserId
        
        // Send notification to all other users
        for (userId in otherUserIds) {
            try {
                createAndSendNotification(
                    userId = userId,
                    title = "Challenge Winner! 🎉",
                    text = "$displayName won the challenge '$challengeTitle' and got $coins coins!",
                    type = "challenge_winner",
                    deeplink = "apptime://screen/challenge_detail/$challengeId"
                )
            } catch (e: Exception) {
                logger.warning("Failed to send challenge winner notification to user $userId: ${e.message}")
                // Continue with other users even if one fails
            }
        }
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
            deeplink = "focus_mode"
        )
    }
    
    /**
     * Send daily limit reached notification
     */
    suspend fun sendDailyLimitNotification(
        userId: String,
        appName: String,
        limitMinutes: Int,
        packageName: String? = null
    ) {
        val deeplink = if (packageName != null) {
            "apptime://screen/app_usage_detail/$packageName"
        } else {
            "statistics"
        }
        
        createAndSendNotification(
            userId = userId,
            title = "Daily Limit Reached ⏰",
            text = "You've reached your daily limit of $limitMinutes minutes for $appName.",
            type = "daily_limit",
            deeplink = deeplink
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
            deeplink = "landing"
        )
    }
    
    // ========== REWARD & COINS NOTIFICATION METHODS ==========
    
    /**
     * Send coins added notification
     * Notifies user when coins are added to their account
     */
    suspend fun sendCoinsAddedNotification(
        userId: String,
        amount: Long,
        source: String,
        description: String?
    ) {
        val sourceText = when (source.uppercase()) {
            "CHALLENGE_WIN" -> "winning a challenge"
            "CHALLENGE_PARTICIPATION" -> "participating in a challenge"
            "DAILY_LOGIN" -> "daily login"
            "STREAK_MILESTONE" -> "reaching a streak milestone"
            "REFERRAL" -> "referring a friend"
            "ACHIEVEMENT" -> "completing an achievement"
            "ADMIN_GRANT" -> "admin grant"
            "PURCHASE" -> "purchase"
            else -> "activity"
        }
        
        val text = if (description != null) {
            "You earned $amount coins for $sourceText: $description"
        } else {
            "You earned $amount coins for $sourceText!"
        }
        
        createAndSendNotification(
            userId = userId,
            title = "Coins Earned! 💰",
            text = text,
            type = "coins_added",
            deeplink = "coin_history"
        )
    }
    
    /**
     * Send reward catalog claimed notification
     * Notifies user when they successfully claim a reward from catalog
     */
    suspend fun sendRewardCatalogClaimedNotification(
        userId: String,
        rewardTitle: String,
        coinPrice: Long,
        transactionNumber: String,
        remainingCoins: Long
    ) {
        createAndSendNotification(
            userId = userId,
            title = "Reward Claimed! 🎁",
            text = "You've successfully claimed '$rewardTitle' for $coinPrice coins! Order #$transactionNumber. You have $remainingCoins coins remaining.",
            type = "reward_claimed",
            deeplink = "apptime://screen/reward_transaction/$transactionNumber"
        )
    }
    
    /**
     * Send transaction status update notification
     * Notifies user about order status changes
     */
    suspend fun sendTransactionStatusNotification(
        userId: String,
        transactionNumber: String,
        rewardTitle: String,
        status: String,
        trackingNumber: String?
    ) {
        val (title, text) = when (status.uppercase()) {
            "PENDING" -> Pair(
                "Order Placed! 📦",
                "Your order #$transactionNumber for '$rewardTitle' has been placed and is awaiting processing."
            )
            "PROCESSING" -> Pair(
                "Order Processing 🔄",
                "Your order #$transactionNumber for '$rewardTitle' is being prepared."
            )
            "SHIPPED" -> {
                val trackingText = if (trackingNumber != null) {
                    " Tracking number: $trackingNumber"
                } else ""
                Pair(
                    "Order Shipped! 🚚",
                    "Your order #$transactionNumber for '$rewardTitle' has been shipped!$trackingText"
                )
            }
            "DELIVERED" -> Pair(
                "Order Delivered! ✅",
                "Your order #$transactionNumber for '$rewardTitle' has been delivered. Enjoy your reward!"
            )
            "CANCELLED" -> Pair(
                "Order Cancelled ❌",
                "Your order #$transactionNumber for '$rewardTitle' has been cancelled. Your coins have been refunded."
            )
            else -> Pair(
                "Order Update 📋",
                "Your order #$transactionNumber for '$rewardTitle' has been updated to: $status"
            )
        }
        
        createAndSendNotification(
            userId = userId,
            title = title,
            text = text,
            type = "transaction_status",
            deeplink = "apptime://screen/reward_transaction/$transactionNumber"
        )
    }
    
    /**
     * Send low balance notification
     * Warns user when their coin balance is low
     */
    suspend fun sendLowBalanceNotification(
        userId: String,
        currentBalance: Long
    ) {
        createAndSendNotification(
            userId = userId,
            title = "Low Coin Balance ⚠️",
            text = "You only have $currentBalance coins remaining. Complete challenges to earn more coins!",
            type = "low_balance",
            deeplink = "challenges"
        )
    }
    
    /**
     * Send reward back in stock notification
     * Notifies users when a previously out-of-stock reward is available again
     */
    suspend fun sendRewardBackInStockNotification(
        userId: String,
        rewardTitle: String,
        rewardCatalogId: Long,
        coinPrice: Long
    ) {
        createAndSendNotification(
            userId = userId,
            title = "Reward Back in Stock! 🎉",
            text = "'$rewardTitle' is back in stock for $coinPrice coins! Claim it before it's gone again.",
            type = "reward_back_in_stock",
            deeplink = "rewards"
        )
    }
    
    /**
     * Send coins spent notification
     * Notifies user when coins are deducted (redemption)
     */
    suspend fun sendCoinsSpentNotification(
        userId: String,
        amount: Long,
        rewardTitle: String,
        remainingCoins: Long
    ) {
        createAndSendNotification(
            userId = userId,
            title = "Coins Spent 💸",
            text = "You spent $amount coins on '$rewardTitle'. You have $remainingCoins coins remaining.",
            type = "coins_spent",
            deeplink = "coin_history"
        )
    }
    
    // ========== ADDITIONAL NOTIFICATION METHODS FOR NEW FEATURES ==========
    
    /**
     * Send app usage high alert notification
     * Warns user when an app usage exceeds a threshold
     */
    suspend fun sendAppUsageHighAlertNotification(
        userId: String,
        appName: String,
        packageName: String,
        usageMinutes: Int,
        thresholdMinutes: Int
    ) {
        createAndSendNotification(
            userId = userId,
            title = "High Usage Alert ⚠️",
            text = "$appName exceeded $thresholdMinutes minutes today. Current usage: $usageMinutes minutes.",
            type = "app_usage_alert",
            deeplink = "apptime://screen/app_usage_detail/$packageName"
        )
    }
    
    /**
     * Send leaderboard rank update notification
     * Notifies user when their leaderboard rank changes significantly
     */
    suspend fun sendLeaderboardRankNotification(
        userId: String,
        newRank: Int,
        previousRank: Int,
        leaderboardType: String = "global"
    ) {
        val rankChange = if (newRank < previousRank) "up" else "down"
        val emoji = if (newRank < previousRank) "📈" else "📉"
        
        createAndSendNotification(
            userId = userId,
            title = "Leaderboard Update $emoji",
            text = "Your rank moved $rankChange from #$previousRank to #$newRank on the $leaderboardType leaderboard!",
            type = "leaderboard_update",
            deeplink = "leaderboard"
        )
    }
    
    /**
     * Send new wallpaper notification
     * Notifies users when new wallpapers are added
     */
    suspend fun sendNewWallpaperNotification(
        userId: String,
        collectionName: String,
        wallpaperCount: Int
    ) {
        createAndSendNotification(
            userId = userId,
            title = "New Wallpapers Available! 🎨",
            text = "$wallpaperCount new wallpapers added to $collectionName collection. Check them out!",
            type = "new_wallpaper",
            deeplink = "wallpaper"
        )
    }
    
    /**
     * Send welcome bonus notification
     * Notifies new users about their welcome bonus
     */
    suspend fun sendWelcomeBonusNotification(
        userId: String,
        bonusAmount: Long
    ) {
        createAndSendNotification(
            userId = userId,
            title = "Welcome to AppTime! 🎉",
            text = "You've received $bonusAmount welcome bonus coins! Start completing challenges to earn more.",
            type = "welcome_bonus",
            deeplink = "landing"
        )
    }
    
    /**
     * Send streak milestone notification
     * Notifies user when they reach a login streak milestone
     */
    suspend fun sendStreakMilestoneNotification(
        userId: String,
        streakDays: Int,
        bonusCoins: Long? = null
    ) {
        val bonusText = if (bonusCoins != null) {
            " You earned $bonusCoins bonus coins!"
        } else {
            ""
        }
        
        createAndSendNotification(
            userId = userId,
            title = "Streak Milestone! 🔥",
            text = "Amazing! You've maintained a $streakDays-day login streak!$bonusText",
            type = "streak_milestone",
            deeplink = "profile"
        )
    }
    
    /**
     * Send friend activity notification
     * Notifies user about friend's achievements or activities
     */
    suspend fun sendFriendActivityNotification(
        userId: String,
        friendUsername: String,
        activityType: String,
        activityDetails: String
    ) {
        val title = when (activityType.uppercase()) {
            "CHALLENGE_WIN" -> "Friend Won Challenge! 🏆"
            "NEW_RECORD" -> "Friend Set New Record! ⭐"
            "ACHIEVEMENT" -> "Friend Unlocked Achievement! 🎖️"
            else -> "Friend Activity 👥"
        }
        
        createAndSendNotification(
            userId = userId,
            title = title,
            text = "$friendUsername $activityDetails",
            type = "friend_activity",
            deeplink = "apptime://screen/record_detail/$friendUsername"
        )
    }
    
    /**
     * Send profile view notification
     * Notifies user when someone views their profile
     */
    suspend fun sendProfileViewNotification(
        userId: String,
        viewerUsername: String
    ) {
        createAndSendNotification(
            userId = userId,
            title = "Profile Viewed 👀",
            text = "$viewerUsername viewed your profile!",
            type = "profile_view",
            deeplink = "profile"
        )
    }
    
    /**
     * Send system maintenance notification
     * Notifies users about scheduled maintenance or updates
     */
    suspend fun sendMaintenanceNotification(
        userId: String,
        maintenanceTime: String,
        duration: String
    ) {
        createAndSendNotification(
            userId = userId,
            title = "Scheduled Maintenance 🔧",
            text = "AppTime will be under maintenance on $maintenanceTime for approximately $duration. Thank you for your patience!",
            type = "maintenance",
            deeplink = "landing"
        )
    }
}


