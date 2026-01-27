package com.apptime.code.rewards

import com.apptime.code.challenges.ChallengeRepository
import com.apptime.code.notifications.NotificationService
import com.apptime.code.notifications.NotificationQueueService
import kotlinx.datetime.Clock

/**
 * Reward service layer - handles business logic
 */
class RewardService(
    private val repository: RewardRepository,
    private val challengeRepository: ChallengeRepository? = null,
    private val notificationService: NotificationService? = null
) {
    
    /**
     * Get all rewards for a user
     */
    suspend fun getUserRewards(
        userId: String,
        type: RewardType? = null,
        source: RewardSource? = null,
        isClaimed: Boolean? = null,
        limit: Int? = null,
        offset: Int = 0
    ): UserRewardsResponse {
        val rewards = repository.getUserRewards(userId, type, source, isClaimed, limit, offset)
        
        val totalPoints = repository.getTotalPoints(userId)
        val totalBadges = repository.getRewardCountByType(userId, RewardType.BADGE)
        val totalTrophies = repository.getRewardCountByType(userId, RewardType.TROPHY)
        val unclaimedCount = repository.getUnclaimedCount(userId)
        
        return UserRewardsResponse(
            userId = userId,
            totalPoints = totalPoints,
            totalBadges = totalBadges,
            totalTrophies = totalTrophies,
            rewards = rewards,
            unclaimedCount = unclaimedCount
        )
    }
    
    /**
     * Get reward summary for a user
     */
    suspend fun getRewardSummary(userId: String): RewardSummary {
        val totalPoints = repository.getTotalPoints(userId)
        val totalBadges = repository.getRewardCountByType(userId, RewardType.BADGE)
        val totalTrophies = repository.getRewardCountByType(userId, RewardType.TROPHY)
        val totalCoupons = repository.getRewardCountByType(userId, RewardType.COUPON)
        val unclaimedRewards = repository.getUnclaimedCount(userId)
        val recentRewards = repository.getUserRewards(userId, limit = 10)
        
        return RewardSummary(
            userId = userId,
            totalPoints = totalPoints,
            totalBadges = totalBadges,
            totalTrophies = totalTrophies,
            totalCoupons = totalCoupons,
            unclaimedRewards = unclaimedRewards,
            recentRewards = recentRewards
        )
    }
    
    /**
     * Claim a reward
     */
    suspend fun claimReward(userId: String, rewardId: Long): ClaimRewardResponse {
        // Verify reward exists and belongs to user
        val reward = repository.getRewardById(rewardId)
            ?: throw IllegalArgumentException("Reward not found")
        
        if (reward.userId != userId) {
            throw IllegalArgumentException("Reward does not belong to user")
        }
        
        if (reward.isClaimed) {
            throw IllegalArgumentException("Reward has already been claimed")
        }
        
        val success = repository.claimReward(rewardId, userId)
        if (!success) {
            throw IllegalStateException("Failed to claim reward")
        }
        
        val claimedReward = repository.getRewardById(rewardId)!!
        
        // Send notification
        notificationService?.sendRewardNotification(
            userId = userId,
            rewardTitle = reward.title,
            rewardDescription = reward.description ?: "Reward claimed successfully",
            rewardId = rewardId
        )
        
        return ClaimRewardResponse(
            rewardId = rewardId,
            message = "Reward '${reward.title}' claimed successfully",
            claimedAt = claimedReward.claimedAt!!
        )
    }
    
    /**
     * Create a reward
     */
    suspend fun createReward(request: CreateRewardRequest): Reward {
        // Validate reward type
        val type = try {
            RewardType.valueOf(request.type.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid reward type: ${request.type}")
        }
        
        // Validate reward source
        val source = try {
            RewardSource.valueOf(request.source.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid reward source: ${request.source}")
        }
        
        val rewardId = repository.createReward(
            userId = request.userId,
            type = type,
            source = source,
            title = request.title,
            description = request.description,
            amount = request.amount,
            metadata = request.metadata,
            challengeId = request.challengeId,
            challengeTitle = request.challengeTitle,
            rank = request.rank
        )
        
        return repository.getRewardById(rewardId)!!
    }
    
    /**
     * Award rewards for challenge winners
     * This method awards rewards to top N participants based on their rankings
     */
    suspend fun awardChallengeRewards(
        challengeId: Long,
        topNRanks: Int = 3
    ): AwardChallengeRewardsResponse {
        if (challengeRepository == null) {
            throw IllegalStateException("Challenge repository not available")
        }
        
        // Get challenge details
        val challenge = challengeRepository.getChallengeById(challengeId)
            ?: throw IllegalArgumentException("Challenge not found")
        
        // Get rankings
        val rankings = challengeRepository.getChallengeRankings(
            challengeId = challengeId,
            challengeType = challenge.challengeType,
            limit = topNRanks
        )
        
        if (rankings.isEmpty()) {
            return AwardChallengeRewardsResponse(
                challengeId = challengeId,
                challengeTitle = challenge.title,
                rewardsAwarded = 0,
                message = "No participants found for this challenge"
            )
        }
        
        // Award points based on rank with proper tie handling
        // Users with the same duration share the same rank
        // Rank 1: 1000 points, Rank 2: 500 points, Rank 3: 250 points, etc.
        val rewardsToCreate = mutableListOf<CreateRewardRequest>()
        var currentRank = 1
        var previousDuration: Long? = null
        
        for (index in rankings.indices) {
            val rankingEntry = rankings[index]
            val userId = rankingEntry.userId
            val totalDuration = rankingEntry.duration
            
            // Update rank when duration changes (ties share the same rank)
            if (previousDuration != null && totalDuration != previousDuration) {
                currentRank = index + 1
            }
            // Note: currentRank is already initialized to 1 for the first entry

            val prize = challengeRepository.getChallengeById(challengeId)!!.prize;

            val coins = prize?.split(" ")?.get(0)?.toLong()
            
            val points = when (currentRank) {
                1 -> coins
                2 -> coins?.div(2)
                3 -> coins?.div(4)
                else -> 0
            }
            
            // Check if user already has a reward for this challenge and rank
            if (!repository.hasChallengeReward(userId, challengeId, currentRank)) {
                if (points != null) {
                    rewardsToCreate.add(
                        CreateRewardRequest(
                            userId = userId,
                            type = RewardType.POINTS.name,
                            source = RewardSource.CHALLENGE_WIN.name,
                            title = "Challenge Winner - Rank $currentRank",
                            description = "Won rank $currentRank in challenge: ${challenge.title}",
                            amount = points.toLong(),
                            challengeId = challengeId,
                            challengeTitle = challenge.title,
                            rank = currentRank
                        )
                    )
                }
            }
            
            previousDuration = totalDuration
        }
        
        if (rewardsToCreate.isEmpty()) {
            return AwardChallengeRewardsResponse(
                challengeId = challengeId,
                challengeTitle = challenge.title,
                rewardsAwarded = 0,
                message = "All participants have already been rewarded"
            )
        }
        
        // Batch create rewards
        repository.batchCreateRewards(rewardsToCreate)
        
        // Get all participants of the challenge for sending notifications
        val allParticipants = challengeRepository?.getChallengeParticipants(challengeId) ?: emptyList()
        
        // Also add coins for each winner and send notifications
        // Coins are the same amount as points
        var coinsAdded = 0
        println("🔄 [RewardService] Starting to award coins for ${rewardsToCreate.size} winners in challenge $challengeId")
        for (rewardRequest in rewardsToCreate) {
            try {
                // Check if coins were already added (prevent duplicates)
                if (!repository.hasChallengeCoin(rewardRequest.userId, challengeId, rewardRequest.rank)) {
                    val addCoinsRequest = AddCoinsRequest(
                        userId = rewardRequest.userId,
                        amount = rewardRequest.amount, // Same amount as points
                        source = CoinSource.CHALLENGE_WIN.name,
                        description = rewardRequest.description,
                        challengeId = rewardRequest.challengeId,
                        challengeTitle = rewardRequest.challengeTitle,
                        rank = rewardRequest.rank
                    )
                    println("💰 [RewardService] Adding ${rewardRequest.amount} coins to user ${rewardRequest.userId} for rank ${rewardRequest.rank}")
                    addCoins(addCoinsRequest)
                    coinsAdded++
                    println("✅ [RewardService] Successfully added coins to user ${rewardRequest.userId}")
                } else {
                    println("⚠️ [RewardService] User ${rewardRequest.userId} already has coins for challenge $challengeId rank ${rewardRequest.rank}, skipping")
                }
                
                // Enqueue notifications instead of sending directly
                // This decouples reward processing from notification sending
                rewardRequest.rank?.let {
                    try {
                        // Enqueue challenge reward notification for the winner
                        NotificationQueueService.enqueueChallengeRewardNotification(
                        userId = rewardRequest.userId,
                        challengeTitle = challenge.title,
                        rank = it,
                        coins = rewardRequest.amount,
                        challengeId = challengeId
                    )
                        println("✅ Enqueued challenge reward notification for user ${rewardRequest.userId}, rank $it, challenge ${challenge.title}")
                    
                    // Get winner's username
                    val winnerUsername = challengeRepository?.getUsername(rewardRequest.userId) ?: rewardRequest.userId
                    
                        // Enqueue notification to all other participants about this winner
                    val otherParticipants = allParticipants.filter { it != rewardRequest.userId }
                    if (otherParticipants.isNotEmpty()) {
                            NotificationQueueService.enqueueChallengeWinnerNotification(
                            winnerUserId = rewardRequest.userId,
                            winnerUsername = winnerUsername,
                            challengeTitle = challenge.title,
                            coins = rewardRequest.amount,
                            challengeId = challengeId,
                            otherUserIds = otherParticipants
                        )
                            println("✅ Enqueued challenge winner notification for ${otherParticipants.size} other participants")
                        }
                    } catch (e: Exception) {
                        println("❌ ERROR: Failed to enqueue notifications for user ${rewardRequest.userId} in challenge $challengeId: ${e.message}")
                        e.printStackTrace()
                        // Don't throw - coins were already added, notification failure shouldn't block
                    }
                }
            } catch (e: Exception) {
                // Log error but continue with other users
                println("Failed to add coins/notification for user ${rewardRequest.userId} in challenge $challengeId: ${e.message}")
            }
        }
        
        return AwardChallengeRewardsResponse(
            challengeId = challengeId,
            challengeTitle = challenge.title,
            rewardsAwarded = rewardsToCreate.size,
            message = "Successfully awarded rewards to ${rewardsToCreate.size} participants"
        )
    }
    
    /**
     * Award participation reward for joining a challenge
     */
    suspend fun awardChallengeParticipationReward(
        userId: String,
        challengeId: Long,
        challengeTitle: String
    ) {
        // Check if user already has a participation reward for this challenge
        val existingRewards = repository.getUserRewards(
            userId = userId,
            source = RewardSource.CHALLENGE_PARTICIPATION
        )
        
        val hasParticipationReward = existingRewards.any { it.challengeId == challengeId }
        
        if (!hasParticipationReward) {
            repository.createReward(
                userId = userId,
                type = RewardType.POINTS,
                source = RewardSource.CHALLENGE_PARTICIPATION,
                title = "Challenge Participation",
                description = "Participated in challenge: $challengeTitle",
                amount = 50L, // Participation points
                challengeId = challengeId,
                challengeTitle = challengeTitle
            )
        }
    }
    
    /**
     * Award daily login reward
     */
    suspend fun awardDailyLoginReward(userId: String, streakDays: Int = 1) {
        val points = when {
            streakDays >= 30 -> 100L // 30+ day streak
            streakDays >= 7 -> 50L   // 7+ day streak
            else -> 10L              // Daily login
        }
        
        repository.createReward(
            userId = userId,
            type = RewardType.POINTS,
            source = RewardSource.DAILY_LOGIN,
            title = if (streakDays > 1) "Daily Login - $streakDays Day Streak" else "Daily Login",
            description = "Logged in for $streakDays day(s)",
            amount = points
        )
    }
    
    /**
     * Award streak milestone reward
     */
    suspend fun awardStreakMilestoneReward(userId: String, streakDays: Int) {
        val milestonePoints = when {
            streakDays == 7 -> 100L
            streakDays == 30 -> 500L
            streakDays == 100 -> 1000L
            streakDays == 365 -> 5000L
            else -> null
        }
        
        if (milestonePoints != null) {
            repository.createReward(
                userId = userId,
                type = RewardType.BADGE,
                source = RewardSource.STREAK_MILESTONE,
                title = "$streakDays Day Streak Milestone",
                description = "Achieved a $streakDays day streak!",
                amount = milestonePoints
            )
        }
    }
    
    /**
     * Get total coins and coin history for a user
     * Uses the dedicated coins table
     */
    suspend fun getUserCoins(
        userId: String,
        source: CoinSource? = null,
        limit: Int? = null,
        offset: Int = 0
    ): CoinsResponse {
        val totalCoins = repository.getTotalCoins(userId)
        val coinHistory = repository.getCoinHistory(
            userId = userId,
            source = source,
            limit = limit,
            offset = offset
        )
        
        return CoinsResponse(
            userId = userId,
            totalCoins = totalCoins,
            coinHistory = coinHistory
        )
    }
    
    /**
     * Add coins to a user's account
     */
    suspend fun addCoins(request: AddCoinsRequest): Coin {
        // Validate coin source
        val source = try {
            CoinSource.valueOf(request.source.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid coin source: ${request.source}")
        }
        
        // Parse expiration date if provided
        val expiresAt = request.expiresAt?.let {
            try {
                kotlinx.datetime.Instant.parse(it)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid expiration date format. Use ISO 8601 format.")
            }
        }
        
        val coinId = repository.addCoins(
            userId = request.userId,
            amount = request.amount,
            source = source,
            description = request.description,
            challengeId = request.challengeId,
            challengeTitle = request.challengeTitle,
            rank = request.rank,
            metadata = request.metadata,
            expiresAt = expiresAt
        )
        
        val coin = repository.getCoinById(coinId)!!
        
        // Send notification for positive coin additions (not redemptions)
        // Skip notifications for challenge wins as they are handled separately
        if (request.amount > 0 && source != CoinSource.CHALLENGE_WIN) {
            try {
                NotificationQueueService.enqueueCoinsAddedNotification(
                    userId = request.userId,
                    amount = request.amount,
                    source = request.source,
                    description = request.description
                )
                println("✅ Enqueued coins added notification for user ${request.userId}, amount: ${request.amount}")
            } catch (e: Exception) {
                println("❌ Failed to enqueue coins added notification: ${e.message}")
                // Don't throw - coin was added successfully, notification failure shouldn't block
            }
        }
        
        return coin
    }
    
    // ========== REWARD CATALOG METHODS ==========
    
    /**
     * Get active reward catalog items
     */
    suspend fun getActiveRewardCatalog(category: String? = null): List<RewardCatalogItem> {
        return repository.getActiveRewardCatalog(category)
    }
    
    /**
     * Get reward catalog item by ID
     */
    suspend fun getRewardCatalogById(catalogId: Long): RewardCatalogItem? {
        return repository.getRewardCatalogById(catalogId)
    }
    
    /**
     * Create a reward catalog item (admin)
     */
    suspend fun createRewardCatalogItem(request: CreateRewardCatalogRequest): RewardCatalogItem {
        // Validate reward type
        val rewardType = try {
            RewardCatalogType.valueOf(request.rewardType.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid reward type. Must be PHYSICAL or DIGITAL")
        }
        
        val catalogId = repository.createRewardCatalogItem(
            title = request.title,
            description = request.description,
            category = request.category,
            rewardType = rewardType.name,
            coinPrice = request.coinPrice,
            imageUrl = request.imageUrl,
            stockQuantity = request.stockQuantity,
            isActive = request.isActive,
            metadata = request.metadata
        )
        
        return repository.getRewardCatalogById(catalogId)!!
    }
    
    /**
     * Update a reward catalog item (admin)
     */
    suspend fun updateRewardCatalogItem(
        catalogId: Long,
        request: CreateRewardCatalogRequest
    ): RewardCatalogItem {
        // Get the current catalog item to check if stock is being replenished
        val currentItem = repository.getRewardCatalogById(catalogId)
        val wasOutOfStock = currentItem != null && 
                            currentItem.stockQuantity != -1 && 
                            currentItem.stockQuantity <= 0
        val willHaveStock = request.stockQuantity == -1 || request.stockQuantity > 0
        
        // Validate reward type
        val rewardType = try {
            RewardCatalogType.valueOf(request.rewardType.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid reward type. Must be PHYSICAL or DIGITAL")
        }
        
        val success = repository.updateRewardCatalogItem(
            catalogId = catalogId,
            title = request.title,
            description = request.description,
            category = request.category,
            rewardType = rewardType.name,
            coinPrice = request.coinPrice,
            imageUrl = request.imageUrl,
            stockQuantity = request.stockQuantity,
            isActive = request.isActive,
            metadata = request.metadata
        )
        
        if (!success) {
            throw IllegalArgumentException("Reward catalog item not found")
        }
        
        val updatedItem = repository.getRewardCatalogById(catalogId)!!
        
        // If item was out of stock and now has stock, notify interested users
        // In a real implementation, you'd want to track which users viewed/wanted this item
        // For now, we'll just log that this feature is available
        if (wasOutOfStock && willHaveStock && request.isActive) {
            println("📦 Reward '${updatedItem.title}' (ID: $catalogId) is back in stock with quantity ${request.stockQuantity}")
            println("💡 Tip: Call notifyRewardBackInStock() to notify interested users")
            // Example usage (you would get userIds from a wishlist/interest table):
            // notifyRewardBackInStock(catalogId, listOf("user1", "user2", "user3"))
        }
        
        return updatedItem
    }
    
    /**
     * Notify users that a reward is back in stock
     * This is a helper method that can be called when restocking items
     * @param catalogId - The catalog item ID
     * @param interestedUserIds - List of user IDs who expressed interest in this reward
     */
    suspend fun notifyRewardBackInStock(
        catalogId: Long,
        interestedUserIds: List<String>
    ) {
        if (interestedUserIds.isEmpty()) {
            return
        }
        
        val catalogItem = repository.getRewardCatalogById(catalogId)
            ?: throw IllegalArgumentException("Reward catalog item not found")
        
        if (!catalogItem.isActive) {
            println("⚠️ Reward '${catalogItem.title}' is not active, skipping notification")
            return
        }
        
        if (catalogItem.stockQuantity != -1 && catalogItem.stockQuantity <= 0) {
            println("⚠️ Reward '${catalogItem.title}' is still out of stock, skipping notification")
            return
        }
        
        try {
            NotificationQueueService.enqueueRewardBackInStockNotification(
                userIds = interestedUserIds,
                rewardTitle = catalogItem.title,
                rewardCatalogId = catalogId,
                coinPrice = catalogItem.coinPrice
            )
            println("✅ Enqueued reward back in stock notification for ${interestedUserIds.size} users, reward: ${catalogItem.title}")
        } catch (e: Exception) {
            println("❌ Failed to enqueue reward back in stock notification: ${e.message}")
        }
    }
    
    // ========== TRANSACTION METHODS ==========
    
    /**
     * Claim a reward (create a transaction)
     * This will:
     * 1. Check if user has any in-progress transactions (prevent multiple claims)
     * 2. Check if user has enough coins
     * 3. Check if reward is available and in stock
     * 4. Deduct coins from user's account
     * 5. Create transaction
     * 6. Update stock if applicable
     */
    suspend fun claimRewardCatalog(
        userId: String,
        request: ClaimRewardCatalogRequest
    ): ClaimRewardCatalogResponse {
        // Validate recipient name is provided
        if (request.recipientName.isBlank()) {
            throw IllegalArgumentException("Recipient name is required. Please provide the name of the person receiving this reward.")
        }
        
        // Check if user has any in-progress transactions
        if (repository.hasInProgressTransaction(userId)) {
            throw IllegalStateException("You already have a pending reward order. Please wait until your current order (PENDING, PROCESSING, or SHIPPED) is delivered or cancelled before placing a new order.")
        }
        
        // Get reward catalog item
        val catalogItem = repository.getRewardCatalogById(request.rewardCatalogId)
            ?: throw IllegalArgumentException("Reward not found. The reward with ID ${request.rewardCatalogId} does not exist in our catalog.")
        
        if (!catalogItem.isActive) {
            throw IllegalStateException("The reward '${catalogItem.title}' is currently unavailable. This item has been temporarily disabled.")
        }
        
        // Validate reward type and required fields
        val rewardType = try {
            RewardCatalogType.valueOf(catalogItem.rewardType.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid reward configuration. Please contact support.")
        }
        
        // Validate required fields based on reward type
        when (rewardType) {
            RewardCatalogType.PHYSICAL -> {
                // Physical rewards require full shipping details
                if (request.shippingAddress.isNullOrBlank()) {
                    throw IllegalArgumentException("Shipping address is required for physical rewards. Please provide your complete delivery address including street, city, state, and postal code.")
                }
                if (request.city.isNullOrBlank()) {
                    throw IllegalArgumentException("City is required for physical rewards. Please provide the city for delivery.")
                }
                if (request.state.isNullOrBlank()) {
                    throw IllegalArgumentException("State/Province is required for physical rewards. Please provide your state or province.")
                }
                if (request.postalCode.isNullOrBlank()) {
                    throw IllegalArgumentException("Postal/ZIP code is required for physical rewards. Please provide your postal or ZIP code.")
                }
            }
            RewardCatalogType.DIGITAL -> {
                // Digital rewards require email or phone
                if (request.recipientEmail.isNullOrBlank() && request.recipientPhone.isNullOrBlank()) {
                    throw IllegalArgumentException("Contact information required. Please provide either an email address or phone number to receive your digital reward.")
                }
                // Validate email format if provided
                if (!request.recipientEmail.isNullOrBlank() && !request.recipientEmail.contains("@")) {
                    throw IllegalArgumentException("Invalid email address format. Please provide a valid email address.")
                }
            }
        }
        
        // Check stock availability
        if (catalogItem.stockQuantity != -1 && catalogItem.stockQuantity <= 0) {
            throw IllegalStateException("Out of stock. The reward '${catalogItem.title}' is currently out of stock. Please check back later or choose another reward.")
        }
        
        // Check if user has enough coins
        val totalCoins = repository.getTotalCoins(userId)
        if (totalCoins < catalogItem.coinPrice) {
            val shortfall = catalogItem.coinPrice - totalCoins
            throw IllegalStateException("Insufficient coins. You need ${catalogItem.coinPrice} coins to claim '${catalogItem.title}', but you only have $totalCoins coins (short by $shortfall coins). Complete more challenges to earn more coins!")
        }
        
        // Deduct coins from user's account
        repository.addCoins(
            userId = userId,
            amount = -catalogItem.coinPrice, // Negative amount for deduction
            source = CoinSource.REDEMPTION,
            description = "Claimed reward: ${catalogItem.title}",
            metadata = "{\"rewardCatalogId\": ${catalogItem.id}, \"transactionNumber\": \"pending\"}"
        )
        
        // Create transaction
        val (transactionId, transactionNumber) = repository.createTransaction(
            userId = userId,
            rewardCatalogId = catalogItem.id,
            rewardTitle = catalogItem.title,
            coinPrice = catalogItem.coinPrice,
            recipientName = request.recipientName,
            recipientPhone = request.recipientPhone,
            recipientEmail = request.recipientEmail,
            upiId = request.upiId,
            shippingAddress = request.shippingAddress,
            city = request.city,
            state = request.state,
            postalCode = request.postalCode,
            country = request.country
        )
        
        // Update stock if not unlimited
        if (catalogItem.stockQuantity != -1) {
            repository.updateRewardCatalogStock(catalogItem.id, -1)
        }
        
        // Get remaining coins
        val remainingCoins = repository.getTotalCoins(userId)
        
        // Send notification for successful catalog claim
        try {
            NotificationQueueService.enqueueRewardCatalogClaimedNotification(
                userId = userId,
                rewardTitle = catalogItem.title,
                coinPrice = catalogItem.coinPrice,
                transactionNumber = transactionNumber,
                remainingCoins = remainingCoins
            )
            println("✅ Enqueued reward catalog claimed notification for user $userId, transaction: $transactionNumber")
        } catch (e: Exception) {
            println("❌ Failed to enqueue reward catalog claimed notification: ${e.message}")
            // Don't throw - transaction was successful, notification failure shouldn't block
        }
        
        // Send low balance warning if remaining coins are below threshold (e.g., 100 coins)
        val LOW_BALANCE_THRESHOLD = 100L
        if (remainingCoins > 0 && remainingCoins <= LOW_BALANCE_THRESHOLD) {
            try {
                NotificationQueueService.enqueueLowBalanceNotification(
                    userId = userId,
                    currentBalance = remainingCoins
                )
                println("⚠️ Enqueued low balance notification for user $userId, balance: $remainingCoins")
            } catch (e: Exception) {
                println("❌ Failed to enqueue low balance notification: ${e.message}")
            }
        }
        
        return ClaimRewardCatalogResponse(
            transactionId = transactionId,
            transactionNumber = transactionNumber,
            message = "Reward claimed successfully! Your order #$transactionNumber has been placed.",
            remainingCoins = remainingCoins
        )
    }
    
    /**
     * Get user transactions
     */
    suspend fun getUserTransactions(userId: String, limit: Int? = null, offset: Int = 0): List<Transaction> {
        return repository.getUserTransactions(userId, limit, offset)
    }
    
    /**
     * Get all transactions (admin)
     */
    suspend fun getAllTransactions(
        status: TransactionStatus? = null,
        limit: Int? = null,
        offset: Int = 0
    ): List<Transaction> {
        return repository.getAllTransactions(status, limit, offset)
    }
    
    /**
     * Get transaction by ID
     */
    suspend fun getTransactionById(transactionId: Long): Transaction? {
        return repository.getTransactionById(transactionId)
    }
    
    /**
     * Update transaction status (admin)
     */
    suspend fun updateTransactionStatus(
        transactionId: Long,
        request: UpdateTransactionStatusRequest
    ): Transaction {
        // Validate status
        val status = try {
            TransactionStatus.valueOf(request.status.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid transaction status: ${request.status}")
        }
        
        val success = repository.updateTransactionStatus(
            transactionId = transactionId,
            status = status,
            adminNotes = request.adminNotes,
            trackingNumber = request.trackingNumber
        )
        
        if (!success) {
            throw IllegalStateException("Failed to update transaction status")
        }
        
        val transaction = repository.getTransactionById(transactionId)!!
        
        // Send notification for transaction status update
        try {
            NotificationQueueService.enqueueTransactionStatusNotification(
                userId = transaction.userId,
                transactionNumber = transaction.transactionNumber,
                rewardTitle = transaction.rewardTitle,
                status = request.status,
                trackingNumber = request.trackingNumber
            )
            println("✅ Enqueued transaction status notification for user ${transaction.userId}, transaction: ${transaction.transactionNumber}, status: ${request.status}")
        } catch (e: Exception) {
            println("❌ Failed to enqueue transaction status notification: ${e.message}")
            // Don't throw - status was updated successfully, notification failure shouldn't block
        }
        
        return transaction
    }
}

