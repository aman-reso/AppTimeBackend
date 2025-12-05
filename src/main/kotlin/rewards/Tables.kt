package com.apptime.code.rewards

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Rewards table - stores all rewards earned by users
 */
object Rewards : Table("rewards") {
    val id = long("id").autoIncrement()
    val userId = varchar("user_id", 255).index()
    val type = varchar("type", 50).index() // POINTS, BADGE, COUPON, TROPHY, CUSTOM
    val rewardSource = varchar("reward_source", 50).index() // CHALLENGE_WIN, CHALLENGE_PARTICIPATION, DAILY_LOGIN, etc.
    val title = varchar("title", 255)
    val description = text("description").nullable()
    val amount = long("amount").default(0L) // For points, this is the point value. For other types, quantity
    val metadata = text("metadata").nullable() // JSON string for additional data
    val challengeId = long("challenge_id").nullable().index() // If reward is from a challenge
    val challengeTitle = varchar("challenge_title", 255).nullable() // Challenge title for reference
    val rank = integer("rank").nullable() // Rank achieved if from challenge
    val earnedAt = timestamp("earned_at").clientDefault { kotlinx.datetime.Clock.System.now() }
    val isClaimed = bool("is_claimed").default(false)
    val claimedAt = timestamp("claimed_at").nullable()
    
    override val primaryKey = PrimaryKey(id)
    
    init {
        index(isUnique = false, userId, type)
        index(isUnique = false, userId, rewardSource)
        index(isUnique = false, userId, isClaimed)
    }
}

/**
 * Coins table - stores coin transactions for users
 */
object Coins : Table("coins") {
    val id = long("id").autoIncrement()
    val userId = varchar("user_id", 255).index()
    val amount = long("amount") // Positive for earned, negative for spent
    val coinSource = varchar("coinSource", 50).index() // CHALLENGE_WIN, CHALLENGE_PARTICIPATION, DAILY_LOGIN, PURCHASE, etc.
    val description = text("description").nullable()
    val challengeId = long("challenge_id").nullable().index() // If coins are from a challenge
    val challengeTitle = varchar("challenge_title", 255).nullable() // Challenge title for reference
    val rank = integer("rank").nullable() // Rank achieved if from challenge
    val metadata = text("metadata").nullable() // JSON string for additional data
    val expiresAt = timestamp("expires_at").nullable() // When the coins expire (null = never expires)
    val createdAt = timestamp("created_at").clientDefault { kotlinx.datetime.Clock.System.now() }
    
    override val primaryKey = PrimaryKey(id)
    
    init {
        index(isUnique = false, userId, coinSource)
        index(isUnique = false, userId, createdAt)
        index(isUnique = false, userId, challengeId)
        index(isUnique = false, userId, expiresAt)
    }
}

