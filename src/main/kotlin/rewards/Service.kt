package com.apptime.code.rewards

import com.apptime.code.challenges.ChallengeRepository
import kotlinx.datetime.Clock

/**
 * Reward service layer - handles business logic
 */
class RewardService(
    private val repository: RewardRepository,
    private val challengeRepository: ChallengeRepository? = null
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
            
            val points = when (currentRank) {
                1 -> 1000L
                2 -> 500L
                3 -> 250L
                else -> 100L
            }
            
            // Check if user already has a reward for this challenge and rank
            if (!repository.hasChallengeReward(userId, challengeId, currentRank)) {
                rewardsToCreate.add(
                    CreateRewardRequest(
                        userId = userId,
                        type = RewardType.POINTS.name,
                        source = RewardSource.CHALLENGE_WIN.name,
                        title = "Challenge Winner - Rank $currentRank",
                        description = "Won rank $currentRank in challenge: ${challenge.title}",
                        amount = points,
                        challengeId = challengeId,
                        challengeTitle = challenge.title,
                        rank = currentRank
                    )
                )
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
        
        // Also add coins for each winner
        // Coins are the same amount as points
        var coinsAdded = 0
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
                    addCoins(addCoinsRequest)
                    coinsAdded++
                }
            } catch (e: Exception) {
                // Log error but continue with other users
                println("Failed to add coins for user ${rewardRequest.userId} in challenge $challengeId: ${e.message}")
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
        
        return repository.getCoinById(coinId)!!
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
        
        return repository.getRewardCatalogById(catalogId)!!
    }
    
    // ========== TRANSACTION METHODS ==========
    
    /**
     * Claim a reward (create a transaction)
     * This will:
     * 1. Check if user has enough coins
     * 2. Check if reward is available and in stock
     * 3. Deduct coins from user's account
     * 4. Create transaction
     * 5. Update stock if applicable
     */
    suspend fun claimRewardCatalog(
        userId: String,
        request: ClaimRewardCatalogRequest
    ): ClaimRewardCatalogResponse {
        // Get reward catalog item
        val catalogItem = repository.getRewardCatalogById(request.rewardCatalogId)
            ?: throw IllegalArgumentException("Reward not found")
        
        if (!catalogItem.isActive) {
            throw IllegalStateException("This reward is not currently available")
        }
        
        // Validate reward type and required fields
        val rewardType = try {
            RewardCatalogType.valueOf(catalogItem.rewardType.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid reward type in catalog item")
        }
        
        // Validate required fields based on reward type
        when (rewardType) {
            RewardCatalogType.PHYSICAL -> {
                // Physical rewards require shipping address
                if (request.shippingAddress.isNullOrBlank()) {
                    throw IllegalArgumentException("Shipping address is required for physical rewards")
                }
            }
            RewardCatalogType.DIGITAL -> {
                // Digital rewards require email or phone
                if (request.recipientEmail.isNullOrBlank() && request.recipientPhone.isNullOrBlank()) {
                    throw IllegalArgumentException("Email or phone number is required for digital rewards")
                }
            }
        }
        
        // Check stock availability
        if (catalogItem.stockQuantity != -1 && catalogItem.stockQuantity <= 0) {
            throw IllegalStateException("This reward is out of stock")
        }
        
        // Check if user has enough coins
        val totalCoins = repository.getTotalCoins(userId)
        if (totalCoins < catalogItem.coinPrice) {
            throw IllegalStateException("Insufficient coins. You have $totalCoins coins but need ${catalogItem.coinPrice} coins")
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
        
        return repository.getTransactionById(transactionId)!!
    }
}

