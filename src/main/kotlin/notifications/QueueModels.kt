package com.apptime.code.notifications

import kotlinx.serialization.Serializable

/**
 * Base interface for notification queue messages
 */
@Serializable
sealed class NotificationMessage {
    abstract val messageId: String
    abstract val timestamp: Long
}

/**
 * Message for challenge reward notification to winner
 */
@Serializable
data class ChallengeRewardNotificationMessage(
    override val messageId: String,
    override val timestamp: Long,
    val userId: String,
    val challengeTitle: String,
    val rank: Int,
    val coins: Long,
    val challengeId: Long
) : NotificationMessage()

/**
 * Message for challenge winner notification to other participants
 */
@Serializable
data class ChallengeWinnerNotificationMessage(
    override val messageId: String,
    override val timestamp: Long,
    val winnerUserId: String,
    val winnerUsername: String,
    val challengeTitle: String,
    val coins: Long,
    val challengeId: Long,
    val otherUserIds: List<String>
) : NotificationMessage()

/**
 * Message for coins added notification
 */
@Serializable
data class CoinsAddedNotificationMessage(
    override val messageId: String,
    override val timestamp: Long,
    val userId: String,
    val amount: Long,
    val source: String, // CoinSource
    val description: String?
) : NotificationMessage()

/**
 * Message for reward catalog claimed notification
 */
@Serializable
data class RewardCatalogClaimedNotificationMessage(
    override val messageId: String,
    override val timestamp: Long,
    val userId: String,
    val rewardTitle: String,
    val coinPrice: Long,
    val transactionNumber: String,
    val remainingCoins: Long
) : NotificationMessage()

/**
 * Message for transaction status update notification
 */
@Serializable
data class TransactionStatusNotificationMessage(
    override val messageId: String,
    override val timestamp: Long,
    val userId: String,
    val transactionNumber: String,
    val rewardTitle: String,
    val status: String, // TransactionStatus
    val trackingNumber: String?
) : NotificationMessage()

/**
 * Message for low balance warning notification
 */
@Serializable
data class LowBalanceNotificationMessage(
    override val messageId: String,
    override val timestamp: Long,
    val userId: String,
    val currentBalance: Long
) : NotificationMessage()

/**
 * Message for reward back in stock notification
 */
@Serializable
data class RewardBackInStockNotificationMessage(
    override val messageId: String,
    override val timestamp: Long,
    val userIds: List<String>,
    val rewardTitle: String,
    val rewardCatalogId: Long,
    val coinPrice: Long
) : NotificationMessage()

