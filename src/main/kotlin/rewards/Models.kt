package com.apptime.code.rewards

import kotlinx.serialization.Serializable

/**
 * Reward type enum - different types of rewards
 */
@Serializable
enum class RewardType {
    POINTS,          // Points that can be accumulated
    BADGE,           // Achievement badges
    COUPON,          // Discount coupons
    TROPHY,          // Trophy/medal rewards
    CUSTOM           // Custom reward type
}

/**
 * Reward source enum - where the reward came from
 */
@Serializable
enum class RewardSource {
    CHALLENGE_WIN,       // Won a challenge (rank-based)
    CHALLENGE_PARTICIPATION, // Participated in a challenge
    DAILY_LOGIN,         // Daily login streak
    STREAK_MILESTONE,    // Reached a streak milestone
    REFERRAL,            // Referred a friend
    ACHIEVEMENT,         // Completed an achievement
    ADMIN_GRANT,         // Admin manually granted
    OTHER                // Other activities
}

/**
 * Reward data model
 */
@Serializable
data class Reward(
    val id: Long,
    val userId: String,
    val type: String, // RewardType as string
    val source: String, // RewardSource as string
    val title: String,
    val description: String? = null,
    val amount: Long = 0, // For points, this is the point value. For other types, can be used for quantity
    val metadata: String? = null, // JSON string for additional data (e.g., challenge ID, rank, etc.)
    val challengeId: Long? = null, // If reward is from a challenge
    val challengeTitle: String? = null, // Challenge title for reference
    val rank: Int? = null, // Rank achieved if from challenge
    val earnedAt: String, // ISO 8601 format
    val isClaimed: Boolean = false,
    val claimedAt: String? = null
)

/**
 * Request to create a reward
 */
@Serializable
data class CreateRewardRequest(
    val userId: String,
    val type: String, // RewardType
    val source: String, // RewardSource
    val title: String,
    val description: String? = null,
    val amount: Long = 0,
    val metadata: String? = null,
    val challengeId: Long? = null,
    val challengeTitle: String? = null,
    val rank: Int? = null
)

/**
 * Response for user rewards
 */
@Serializable
data class UserRewardsResponse(
    val userId: String,
    val totalPoints: Long = 0,
    val totalBadges: Int = 0,
    val totalTrophies: Int = 0,
    val rewards: List<Reward>,
    val unclaimedCount: Int = 0
)

/**
 * Request to claim a reward
 */
@Serializable
data class ClaimRewardRequest(
    val rewardId: Long
)

/**
 * Response for claiming a reward
 */
@Serializable
data class ClaimRewardResponse(
    val rewardId: Long,
    val message: String,
    val claimedAt: String
)

/**
 * Reward summary for user
 */
@Serializable
data class RewardSummary(
    val userId: String,
    val totalPoints: Long = 0,
    val totalBadges: Int = 0,
    val totalTrophies: Int = 0,
    val totalCoupons: Int = 0,
    val unclaimedRewards: Int = 0,
    val recentRewards: List<Reward> = emptyList()
)

/**
 * Request to award challenge rewards
 * This can be called after a challenge ends to award rewards to winners
 */
@Serializable
data class AwardChallengeRewardsRequest(
    val challengeId: Long,
    val topNRanks: Int = 3 // Number of top ranks to reward (default: top 3)
)

/**
 * Response for awarding challenge rewards
 */
@Serializable
data class AwardChallengeRewardsResponse(
    val challengeId: Long,
    val challengeTitle: String,
    val rewardsAwarded: Int,
    val message: String
)

/**
 * Coin transaction data model
 */
@Serializable
data class Coin(
    val id: Long,
    val userId: String,
    val amount: Long, // Positive for earned, negative for spent
    val source: String, // CoinSource as string
    val description: String? = null,
    val challengeId: Long? = null,
    val challengeTitle: String? = null,
    val rank: Int? = null,
    val metadata: String? = null,
    val expiresAt: String? = null, // ISO 8601 format, null = never expires
    val createdAt: String // ISO 8601 format
)

/**
 * Coin source enum - where the coins came from or went to
 */
@Serializable
enum class CoinSource {
    CHALLENGE_WIN,           // Won a challenge (rank-based)
    CHALLENGE_PARTICIPATION, // Participated in a challenge
    DAILY_LOGIN,             // Daily login streak
    STREAK_MILESTONE,        // Reached a streak milestone
    REFERRAL,                // Referred a friend
    ACHIEVEMENT,             // Completed an achievement
    ADMIN_GRANT,             // Admin manually granted
    PURCHASE,                // Purchased coins
    REDEMPTION,              // Redeemed coins for rewards
    OTHER                    // Other activities
}

/**
 * Request to add coins
 */
@Serializable
data class AddCoinsRequest(
    val userId: String,
    val amount: Long,
    val source: String, // CoinSource
    val description: String? = null,
    val challengeId: Long? = null,
    val challengeTitle: String? = null,
    val rank: Int? = null,
    val metadata: String? = null,
    val expiresAt: String? = null // ISO 8601 format, null = never expires
)

/**
 * Response for user coins (total coins and coin history)
 */
@Serializable
data class CoinsResponse(
    val userId: String,
    val totalCoins: Long = 0,
    val coinHistory: List<Coin> = emptyList()
)

/**
 * Transaction status enum
 */
@Serializable
enum class TransactionStatus {
    PENDING,        // Order placed, waiting to be processed
    PROCESSING,     // Order is being prepared
    SHIPPED,        // Order has been shipped
    DELIVERED,      // Order has been delivered
    CANCELLED       // Order was cancelled
}

/**
 * Reward Catalog Item - available product/reward that can be claimed
 */
@Serializable
data class RewardCatalogItem(
    val id: Long,
    val title: String,
    val description: String? = null,
    val category: String? = null,
    val coinPrice: Long,
    val imageUrl: String? = null,
    val stockQuantity: Int, // -1 for unlimited
    val isActive: Boolean = true,
    val metadata: String? = null,
    val createdAt: String, // ISO 8601 format
    val updatedAt: String // ISO 8601 format
)

/**
 * Request to create/update a reward catalog item
 */
@Serializable
data class CreateRewardCatalogRequest(
    val title: String,
    val description: String? = null,
    val category: String? = null,
    val coinPrice: Long,
    val imageUrl: String? = null,
    val stockQuantity: Int = -1, // -1 for unlimited
    val isActive: Boolean = true,
    val metadata: String? = null
)

/**
 * Transaction/Order data model
 */
@Serializable
data class Transaction(
    val id: Long,
    val userId: String,
    val rewardCatalogId: Long,
    val rewardTitle: String,
    val coinPrice: Long,
    val status: String, // TransactionStatus as string
    val transactionNumber: String,
    
    // Shipping information
    val recipientName: String,
    val recipientPhone: String? = null,
    val recipientEmail: String? = null,
    val shippingAddress: String,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    
    // Admin fields
    val adminNotes: String? = null,
    val trackingNumber: String? = null,
    val shippedAt: String? = null,
    val deliveredAt: String? = null,
    
    val createdAt: String, // ISO 8601 format
    val updatedAt: String // ISO 8601 format
)

/**
 * Request to claim a reward (create a transaction)
 */
@Serializable
data class ClaimRewardCatalogRequest(
    val rewardCatalogId: Long,
    val recipientName: String,
    val recipientPhone: String? = null,
    val recipientEmail: String? = null,
    val shippingAddress: String,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String? = null
)

/**
 * Response for claiming a reward
 */
@Serializable
data class ClaimRewardCatalogResponse(
    val transactionId: Long,
    val transactionNumber: String,
    val message: String,
    val remainingCoins: Long
)

/**
 * Request to update transaction status (admin only)
 */
@Serializable
data class UpdateTransactionStatusRequest(
    val status: String, // TransactionStatus
    val adminNotes: String? = null,
    val trackingNumber: String? = null
)

