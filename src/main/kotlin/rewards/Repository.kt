package com.apptime.code.rewards

import com.apptime.code.common.dbTransaction
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class RewardRepository {
    
    /**
     * Create a new reward
     */
    fun createReward(
        userId: String,
        type: RewardType,
        source: RewardSource,
        title: String,
        description: String? = null,
        amount: Long = 0,
        metadata: String? = null,
        challengeId: Long? = null,
        challengeTitle: String? = null,
        rank: Int? = null
    ): Long {
        return dbTransaction {
            Rewards.insert {
                it[Rewards.userId] = userId
                it[Rewards.type] = type.name
                it[Rewards.rewardSource] = source.name
                it[Rewards.title] = title
                it[Rewards.description] = description
                it[Rewards.amount] = amount
                it[Rewards.metadata] = metadata
                it[Rewards.challengeId] = challengeId
                it[Rewards.challengeTitle] = challengeTitle
                it[Rewards.rank] = rank
            }[Rewards.id]
        }
    }
    
    /**
     * Get all rewards for a user
     */
    fun getUserRewards(
        userId: String,
        type: RewardType? = null,
        source: RewardSource? = null,
        isClaimed: Boolean? = null,
        limit: Int? = null,
        offset: Int = 0
    ): List<Reward> {
        return dbTransaction {
            var query = Rewards.select { Rewards.userId eq userId }
            
            if (type != null) {
                query = query.andWhere { Rewards.type eq type.name }
            }
            
            if (source != null) {
                query = query.andWhere { Rewards.rewardSource eq source.name }
            }
            
            if (isClaimed != null) {
                query = query.andWhere { Rewards.isClaimed eq isClaimed }
            }
            
            query = query.orderBy(Rewards.earnedAt to SortOrder.DESC)
            
            if (limit != null) {
                query = query.limit(limit, offset.toLong())
            }
            
            query.map { row ->
                Reward(
                    id = row[Rewards.id],
                    userId = row[Rewards.userId],
                    type = row[Rewards.type],
                    source = row[Rewards.rewardSource],
                    title = row[Rewards.title],
                    description = row[Rewards.description],
                    amount = row[Rewards.amount],
                    metadata = row[Rewards.metadata],
                    challengeId = row[Rewards.challengeId],
                    challengeTitle = row[Rewards.challengeTitle],
                    rank = row[Rewards.rank],
                    earnedAt = row[Rewards.earnedAt].toString(),
                    isClaimed = row[Rewards.isClaimed],
                    claimedAt = row[Rewards.claimedAt]?.toString()
                )
            }
        }
    }
    
    /**
     * Get reward by ID
     */
    fun getRewardById(rewardId: Long): Reward? {
        return dbTransaction {
            Rewards.select { Rewards.id eq rewardId }
                .firstOrNull()
                ?.let { row ->
                    Reward(
                        id = row[Rewards.id],
                        userId = row[Rewards.userId],
                        type = row[Rewards.type],
                        source = row[Rewards.rewardSource],
                        title = row[Rewards.title],
                        description = row[Rewards.description],
                        amount = row[Rewards.amount],
                        metadata = row[Rewards.metadata],
                        challengeId = row[Rewards.challengeId],
                        challengeTitle = row[Rewards.challengeTitle],
                        rank = row[Rewards.rank],
                        earnedAt = row[Rewards.earnedAt].toString(),
                        isClaimed = row[Rewards.isClaimed],
                        claimedAt = row[Rewards.claimedAt]?.toString()
                    )
                }
        }
    }
    
    /**
     * Claim a reward
     */
    fun claimReward(rewardId: Long, userId: String): Boolean {
        return dbTransaction {
            val reward = Rewards.select { 
                (Rewards.id eq rewardId) and (Rewards.userId eq userId)
            }.firstOrNull()
            
            if (reward == null || reward[Rewards.isClaimed]) {
                false
            } else {
                Rewards.update({ Rewards.id eq rewardId }) {
                    it[Rewards.isClaimed] = true
                    it[Rewards.claimedAt] = kotlinx.datetime.Clock.System.now()
                }
                true
            }
        }
    }
    
    /**
     * Get total points for a user
     */
    fun getTotalPoints(userId: String): Long {
        return dbTransaction {
            Rewards.select {
                (Rewards.userId eq userId) and
                (Rewards.type eq RewardType.POINTS.name)
            }.sumOf { it[Rewards.amount] }
        }
    }
    
    /**
     * Get count of rewards by type for a user
     */
    fun getRewardCountByType(userId: String, type: RewardType): Int {
        return dbTransaction {
            Rewards.select {
                (Rewards.userId eq userId) and
                (Rewards.type eq type.name)
            }.count().toInt()
        }
    }
    
    /**
     * Get unclaimed rewards count for a user
     */
    fun getUnclaimedCount(userId: String): Int {
        return dbTransaction {
            Rewards.select {
                (Rewards.userId eq userId) and
                (Rewards.isClaimed eq false)
            }.count().toInt()
        }
    }
    
    /**
     * Check if user already has a reward for a specific challenge and rank
     * (to prevent duplicate rewards)
     */
    fun hasChallengeReward(userId: String, challengeId: Long, rank: Int?): Boolean {
        return dbTransaction {
            val query = Rewards.select {
                (Rewards.userId eq userId) and
                (Rewards.challengeId eq challengeId) and
                (Rewards.rewardSource eq RewardSource.CHALLENGE_WIN.name)
            }
            
            if (rank != null) {
                query.andWhere { Rewards.rank eq rank }
            }
            
            query.count() > 0
        }
    }
    
    /**
     * Get rewards by challenge ID
     */
    fun getRewardsByChallenge(challengeId: Long): List<Reward> {
        return dbTransaction {
            Rewards.select { Rewards.challengeId eq challengeId }
                .orderBy(Rewards.rank to SortOrder.ASC_NULLS_LAST)
                .map { row ->
                    Reward(
                        id = row[Rewards.id],
                        userId = row[Rewards.userId],
                        type = row[Rewards.type],
                        source = row[Rewards.rewardSource],
                        title = row[Rewards.title],
                        description = row[Rewards.description],
                        amount = row[Rewards.amount],
                        metadata = row[Rewards.metadata],
                        challengeId = row[Rewards.challengeId],
                        challengeTitle = row[Rewards.challengeTitle],
                        rank = row[Rewards.rank],
                        earnedAt = row[Rewards.earnedAt].toString(),
                        isClaimed = row[Rewards.isClaimed],
                        claimedAt = row[Rewards.claimedAt]?.toString()
                    )
                }
        }
    }
    
    /**
     * Batch create rewards
     */
    fun batchCreateRewards(rewards: List<CreateRewardRequest>): List<Long> {
        return dbTransaction {
            rewards.map { reward ->
                Rewards.insert {
                    it[Rewards.userId] = reward.userId
                    it[Rewards.type] = reward.type
                    it[Rewards.rewardSource] = reward.source
                    it[Rewards.title] = reward.title
                    it[Rewards.description] = reward.description
                    it[Rewards.amount] = reward.amount
                    it[Rewards.metadata] = reward.metadata
                    it[Rewards.challengeId] = reward.challengeId
                    it[Rewards.challengeTitle] = reward.challengeTitle
                    it[Rewards.rank] = reward.rank
                }[Rewards.id]
            }
        }
    }
    
    // ========== COINS METHODS ==========
    
    /**
     * Add coins to a user's account
     */
    fun addCoins(
        userId: String,
        amount: Long,
        source: CoinSource,
        description: String? = null,
        challengeId: Long? = null,
        challengeTitle: String? = null,
        rank: Int? = null,
        metadata: String? = null,
        expiresAt: kotlinx.datetime.Instant? = null
    ): Long {
        return dbTransaction {
            Coins.insert {
                it[Coins.userId] = userId
                it[Coins.amount] = amount
                it[Coins.coinSource] = source.name
                it[Coins.description] = description
                it[Coins.challengeId] = challengeId
                it[Coins.challengeTitle] = challengeTitle
                it[Coins.rank] = rank
                it[Coins.metadata] = metadata
                it[Coins.expiresAt] = expiresAt
            }[Coins.id]
        }
    }
    
    /**
     * Get total coins for a user (sum of all non-expired coin transactions)
     */
    fun getTotalCoins(userId: String): Long {
        return dbTransaction {
            val now = kotlinx.datetime.Clock.System.now()
            Coins.select { 
                (Coins.userId eq userId) and
                ((Coins.expiresAt.isNull()) or (Coins.expiresAt greater now))
            }
                .sumOf { it[Coins.amount] }
        }
    }
    
    /**
     * Get coin history for a user
     */
    fun getCoinHistory(
        userId: String,
        source: CoinSource? = null,
        limit: Int? = null,
        offset: Int = 0
    ): List<Coin> {
        return dbTransaction {
            var query = Coins.select { Coins.userId eq userId }
            
            if (source != null) {
                query = query.andWhere { Coins.coinSource eq source.name }
            }
            
            query = query.orderBy(Coins.createdAt to SortOrder.DESC)
            
            if (limit != null) {
                query = query.limit(limit, offset.toLong())
            }
            
            query.map { row ->
                Coin(
                    id = row[Coins.id],
                    userId = row[Coins.userId],
                    amount = row[Coins.amount],
                    source = row[Coins.coinSource],
                    description = row[Coins.description],
                    challengeId = row[Coins.challengeId],
                    challengeTitle = row[Coins.challengeTitle],
                    rank = row[Coins.rank],
                    metadata = row[Coins.metadata],
                    expiresAt = row[Coins.expiresAt]?.toString(),
                    createdAt = row[Coins.createdAt].toString()
                )
            }
        }
    }
    
    /**
     * Get coin by ID
     */
    fun getCoinById(coinId: Long): Coin? {
        return dbTransaction {
            Coins.select { Coins.id eq coinId }
                .firstOrNull()
                ?.let { row ->
                    Coin(
                        id = row[Coins.id],
                        userId = row[Coins.userId],
                        amount = row[Coins.amount],
                        source = row[Coins.coinSource],
                        description = row[Coins.description],
                        challengeId = row[Coins.challengeId],
                        challengeTitle = row[Coins.challengeTitle],
                        rank = row[Coins.rank],
                        metadata = row[Coins.metadata],
                        expiresAt = row[Coins.expiresAt]?.toString(),
                        createdAt = row[Coins.createdAt].toString()
                    )
                }
        }
    }
    
    // ========== REWARD CATALOG METHODS ==========
    
    /**
     * Get all active reward catalog items
     */
    fun getActiveRewardCatalog(category: String? = null): List<RewardCatalogItem> {
        return dbTransaction {
            var query = RewardCatalog.select { RewardCatalog.isActive eq true }
            
            if (category != null) {
                query = query.andWhere { RewardCatalog.category eq category }
            }
            
            query.orderBy(RewardCatalog.createdAt to SortOrder.DESC)
                .map { row ->
                    RewardCatalogItem(
                        id = row[RewardCatalog.id],
                        title = row[RewardCatalog.title],
                        description = row[RewardCatalog.description],
                        category = row[RewardCatalog.category],
                        coinPrice = row[RewardCatalog.coinPrice],
                        imageUrl = row[RewardCatalog.imageUrl],
                        stockQuantity = row[RewardCatalog.stockQuantity],
                        isActive = row[RewardCatalog.isActive],
                        metadata = row[RewardCatalog.metadata],
                        createdAt = row[RewardCatalog.createdAt].toString(),
                        updatedAt = row[RewardCatalog.updatedAt].toString()
                    )
                }
        }
    }
    
    /**
     * Get reward catalog item by ID
     */
    fun getRewardCatalogById(catalogId: Long): RewardCatalogItem? {
        return dbTransaction {
            RewardCatalog.select { RewardCatalog.id eq catalogId }
                .firstOrNull()
                ?.let { row ->
                    RewardCatalogItem(
                        id = row[RewardCatalog.id],
                        title = row[RewardCatalog.title],
                        description = row[RewardCatalog.description],
                        category = row[RewardCatalog.category],
                        coinPrice = row[RewardCatalog.coinPrice],
                        imageUrl = row[RewardCatalog.imageUrl],
                        stockQuantity = row[RewardCatalog.stockQuantity],
                        isActive = row[RewardCatalog.isActive],
                        metadata = row[RewardCatalog.metadata],
                        createdAt = row[RewardCatalog.createdAt].toString(),
                        updatedAt = row[RewardCatalog.updatedAt].toString()
                    )
                }
        }
    }
    
    /**
     * Create a reward catalog item
     */
    fun createRewardCatalogItem(
        title: String,
        description: String? = null,
        category: String? = null,
        coinPrice: Long,
        imageUrl: String? = null,
        stockQuantity: Int = -1,
        isActive: Boolean = true,
        metadata: String? = null
    ): Long {
        return dbTransaction {
            RewardCatalog.insert {
                it[RewardCatalog.title] = title
                it[RewardCatalog.description] = description
                it[RewardCatalog.category] = category
                it[RewardCatalog.coinPrice] = coinPrice
                it[RewardCatalog.imageUrl] = imageUrl
                it[RewardCatalog.stockQuantity] = stockQuantity
                it[RewardCatalog.isActive] = isActive
                it[RewardCatalog.metadata] = metadata
                it[RewardCatalog.updatedAt] = kotlinx.datetime.Clock.System.now()
            }[RewardCatalog.id]
        }
    }
    
    /**
     * Update reward catalog item stock
     */
    fun updateRewardCatalogStock(catalogId: Long, quantityChange: Int): Boolean {
        return dbTransaction {
            val item = RewardCatalog.select { RewardCatalog.id eq catalogId }.firstOrNull()
            if (item == null) {
                false
            } else {
                val currentStock = item[RewardCatalog.stockQuantity]
                // Only update if not unlimited (-1)
                if (currentStock == -1) {
                    true // Unlimited stock, no update needed
                } else {
                    val newStock = (currentStock + quantityChange).coerceAtLeast(0)
                    RewardCatalog.update({ RewardCatalog.id eq catalogId }) {
                        it[RewardCatalog.stockQuantity] = newStock
                        it[RewardCatalog.updatedAt] = kotlinx.datetime.Clock.System.now()
                    }
                    true
                }
            }
        }
    }
    
    // ========== TRANSACTION METHODS ==========
    
    /**
     * Create a transaction (user claims a reward)
     */
    fun createTransaction(
        userId: String,
        rewardCatalogId: Long,
        rewardTitle: String,
        coinPrice: Long,
        recipientName: String,
        recipientPhone: String? = null,
        recipientEmail: String? = null,
        shippingAddress: String,
        city: String? = null,
        state: String? = null,
        postalCode: String? = null,
        country: String? = null
    ): Pair<Long, String> {
        return dbTransaction {
            // Generate unique transaction number
            val transactionNumber = "TXN-${System.currentTimeMillis()}-${userId.take(8).uppercase()}"
            
            val transactionId = Transactions.insert {
                it[Transactions.userId] = userId
                it[Transactions.rewardCatalogId] = rewardCatalogId
                it[Transactions.rewardTitle] = rewardTitle
                it[Transactions.coinPrice] = coinPrice
                it[Transactions.status] = TransactionStatus.PENDING.name
                it[Transactions.transactionNumber] = transactionNumber
                it[Transactions.recipientName] = recipientName
                it[Transactions.recipientPhone] = recipientPhone
                it[Transactions.recipientEmail] = recipientEmail
                it[Transactions.shippingAddress] = shippingAddress
                it[Transactions.city] = city
                it[Transactions.state] = state
                it[Transactions.postalCode] = postalCode
                it[Transactions.country] = country
                it[Transactions.updatedAt] = kotlinx.datetime.Clock.System.now()
            }[Transactions.id]
            
            transactionId to transactionNumber
        }
    }
    
    /**
     * Get transactions for a user
     */
    fun getUserTransactions(userId: String, limit: Int? = null, offset: Int = 0): List<Transaction> {
        return dbTransaction {
            var query = Transactions.select { Transactions.userId eq userId }
                .orderBy(Transactions.createdAt to SortOrder.DESC)
            
            if (limit != null) {
                query = query.limit(limit, offset.toLong())
            }
            
            query.map { row ->
                Transaction(
                    id = row[Transactions.id],
                    userId = row[Transactions.userId],
                    rewardCatalogId = row[Transactions.rewardCatalogId],
                    rewardTitle = row[Transactions.rewardTitle],
                    coinPrice = row[Transactions.coinPrice],
                    status = row[Transactions.status],
                    transactionNumber = row[Transactions.transactionNumber],
                    recipientName = row[Transactions.recipientName],
                    recipientPhone = row[Transactions.recipientPhone],
                    recipientEmail = row[Transactions.recipientEmail],
                    shippingAddress = row[Transactions.shippingAddress],
                    city = row[Transactions.city],
                    state = row[Transactions.state],
                    postalCode = row[Transactions.postalCode],
                    country = row[Transactions.country],
                    adminNotes = row[Transactions.adminNotes],
                    trackingNumber = row[Transactions.trackingNumber],
                    shippedAt = row[Transactions.shippedAt]?.toString(),
                    deliveredAt = row[Transactions.deliveredAt]?.toString(),
                    createdAt = row[Transactions.createdAt].toString(),
                    updatedAt = row[Transactions.updatedAt].toString()
                )
            }
        }
    }
    
    /**
     * Get all transactions (admin)
     */
    fun getAllTransactions(
        status: TransactionStatus? = null,
        limit: Int? = null,
        offset: Int = 0
    ): List<Transaction> {
        return dbTransaction {
            var query = Transactions.selectAll()
            
            if (status != null) {
                query = query.andWhere { Transactions.status eq status.name }
            }
            
            query = query.orderBy(Transactions.createdAt to SortOrder.DESC)
            
            if (limit != null) {
                query = query.limit(limit, offset.toLong())
            }
            
            query.map { row ->
                Transaction(
                    id = row[Transactions.id],
                    userId = row[Transactions.userId],
                    rewardCatalogId = row[Transactions.rewardCatalogId],
                    rewardTitle = row[Transactions.rewardTitle],
                    coinPrice = row[Transactions.coinPrice],
                    status = row[Transactions.status],
                    transactionNumber = row[Transactions.transactionNumber],
                    recipientName = row[Transactions.recipientName],
                    recipientPhone = row[Transactions.recipientPhone],
                    recipientEmail = row[Transactions.recipientEmail],
                    shippingAddress = row[Transactions.shippingAddress],
                    city = row[Transactions.city],
                    state = row[Transactions.state],
                    postalCode = row[Transactions.postalCode],
                    country = row[Transactions.country],
                    adminNotes = row[Transactions.adminNotes],
                    trackingNumber = row[Transactions.trackingNumber],
                    shippedAt = row[Transactions.shippedAt]?.toString(),
                    deliveredAt = row[Transactions.deliveredAt]?.toString(),
                    createdAt = row[Transactions.createdAt].toString(),
                    updatedAt = row[Transactions.updatedAt].toString()
                )
            }
        }
    }
    
    /**
     * Get transaction by ID
     */
    fun getTransactionById(transactionId: Long): Transaction? {
        return dbTransaction {
            Transactions.select { Transactions.id eq transactionId }
                .firstOrNull()
                ?.let { row ->
                    Transaction(
                        id = row[Transactions.id],
                        userId = row[Transactions.userId],
                        rewardCatalogId = row[Transactions.rewardCatalogId],
                        rewardTitle = row[Transactions.rewardTitle],
                        coinPrice = row[Transactions.coinPrice],
                        status = row[Transactions.status],
                        transactionNumber = row[Transactions.transactionNumber],
                        recipientName = row[Transactions.recipientName],
                        recipientPhone = row[Transactions.recipientPhone],
                        recipientEmail = row[Transactions.recipientEmail],
                        shippingAddress = row[Transactions.shippingAddress],
                        city = row[Transactions.city],
                        state = row[Transactions.state],
                        postalCode = row[Transactions.postalCode],
                        country = row[Transactions.country],
                        adminNotes = row[Transactions.adminNotes],
                        trackingNumber = row[Transactions.trackingNumber],
                        shippedAt = row[Transactions.shippedAt]?.toString(),
                        deliveredAt = row[Transactions.deliveredAt]?.toString(),
                        createdAt = row[Transactions.createdAt].toString(),
                        updatedAt = row[Transactions.updatedAt].toString()
                    )
                }
        }
    }
    
    /**
     * Update transaction status (admin)
     */
    fun updateTransactionStatus(
        transactionId: Long,
        status: TransactionStatus,
        adminNotes: String? = null,
        trackingNumber: String? = null
    ): Boolean {
        return dbTransaction {
            val now = kotlinx.datetime.Clock.System.now()
            Transactions.update({ Transactions.id eq transactionId }) {
                it[Transactions.status] = status.name
                it[Transactions.adminNotes] = adminNotes
                it[Transactions.trackingNumber] = trackingNumber
                it[Transactions.updatedAt] = now
                
                // Set shippedAt when status changes to SHIPPED
                if (status == TransactionStatus.SHIPPED && trackingNumber != null) {
                    it[Transactions.shippedAt] = now
                }
                
                // Set deliveredAt when status changes to DELIVERED
                if (status == TransactionStatus.DELIVERED) {
                    it[Transactions.deliveredAt] = now
                }
            }
            true
        }
    }
}

