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
}

