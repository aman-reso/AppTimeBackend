package com.apptime.code.challenges

import com.apptime.code.common.dbTransaction
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class ChallengeRepository {
    
    /**
     * Get all active challenges (where isActive = true and endTime > now)
     */
    fun getActiveChallenges(userId: String? = null): List<ActiveChallenge> {
        return dbTransaction {
            val now = Clock.System.now()
            val joinedChallengeIds = if (userId != null) {
                ChallengeParticipants.slice(ChallengeParticipants.challengeId)
                    .select { ChallengeParticipants.userId eq userId }
                    .map { it[ChallengeParticipants.challengeId] }
                    .toSet()
            } else {
                emptySet()
            }
            val challenges = Challenges.select {
                (Challenges.isActive eq true) and
                (Challenges.endTime greater now)
            }
            .orderBy(Challenges.startTime to SortOrder.ASC)
            .map { row -> row[Challenges.id] to row }
            
            // Get participant counts for all challenges in one query
            val challengeIds = challenges.map { it.first }
            val participantCounts = if (challengeIds.isNotEmpty()) {
                ChallengeParticipants.select {
                    ChallengeParticipants.challengeId inList challengeIds
                }
                .groupBy { it[ChallengeParticipants.challengeId] }
                .mapValues { it.value.size }
            } else {
                emptyMap()
            }
            
            challenges.map { (challengeId, row) ->
                val tagsString = row[Challenges.tags]
                val tagsList = if (tagsString.isNullOrBlank()) {
                    emptyList()
                } else {
                    tagsString.split(",").map { it.trim() }.filter { it.isNotBlank() }
                }
                ActiveChallenge(
                    id = challengeId,
                    title = row[Challenges.title],
                    description = row[Challenges.description],
                    reward = row[Challenges.reward],
                    prize = row[Challenges.prize],
                    rules = row[Challenges.rules],
                    displayType = row[Challenges.displayType],
                    tags = tagsList,
                    sponsor = row[Challenges.sponsor],
                    startTime = row[Challenges.startTime].toString(),
                    endTime = row[Challenges.endTime].toString(),
                    thumbnail = row[Challenges.thumbnail],
                    packageNames = row[Challenges.packageNames],
                    participantCount = participantCounts[challengeId] ?: 0,
                    hasJoined = joinedChallengeIds.contains(challengeId)
                )
            }
        }
    }
    
    /**
     * Get challenge by ID
     */
    fun getChallengeById(challengeId: Long): Challenge? {
        return dbTransaction {
            Challenges.select { Challenges.id eq challengeId }
                .firstOrNull()
                ?.let { row ->
                    Challenge(
                        id = row[Challenges.id],
                        title = row[Challenges.title],
                        description = row[Challenges.description],
                        reward = row[Challenges.reward],
                        startTime = row[Challenges.startTime].toString(),
                        endTime = row[Challenges.endTime].toString(),
                        thumbnail = row[Challenges.thumbnail],
                        challengeType = row[Challenges.challengeType],
                        isActive = row[Challenges.isActive],
                        createdAt = row[Challenges.createdAt].toString()
                    )
                }
        }
    }
    
    /**
     * Check if user has already joined a challenge
     */
    fun hasUserJoinedChallenge(userId: String, challengeId: Long): Boolean {
        return dbTransaction {
            ChallengeParticipants.select {
                (ChallengeParticipants.challengeId eq challengeId) and
                (ChallengeParticipants.userId eq userId)
            }.count() > 0
        }
    }
    
    /**
     * Join a challenge
     */
    fun joinChallenge(userId: String, challengeId: Long): String {
        return dbTransaction {
            ChallengeParticipants.insert {
                it[ChallengeParticipants.challengeId] = challengeId
                it[ChallengeParticipants.userId] = userId
            }
            ChallengeParticipants.select {
                (ChallengeParticipants.challengeId eq challengeId) and
                (ChallengeParticipants.userId eq userId)
            }.first()[ChallengeParticipants.joinedAt].toString()
        }
    }
    
    /**
     * Get all challenges for a user (including past ones)
     */
    fun getUserChallenges(userId: String): List<UserChallenge> {
        return dbTransaction {
            val now = Clock.System.now()
            (ChallengeParticipants innerJoin Challenges)
                .select { ChallengeParticipants.userId eq userId }
                .orderBy(Challenges.endTime to SortOrder.DESC)
                .map { row ->
                    val endTime = row[Challenges.endTime]
                    UserChallenge(
                        id = row[Challenges.id],
                        title = row[Challenges.title],
                        description = row[Challenges.description],
                        reward = row[Challenges.reward],
                        startTime = row[Challenges.startTime].toString(),
                        endTime = endTime.toString(),
                        thumbnail = row[Challenges.thumbnail],
                        challengeType = row[Challenges.challengeType],
                        isActive = row[Challenges.isActive],
                        joinedAt = row[ChallengeParticipants.joinedAt].toString(),
                        isPast = endTime < now
                    )
                }
        }
    }
    
    /**
     * Get challenge detail with participant count
     */
    fun getChallengeDetail(challengeId: Long): ChallengeDetail? {
        return dbTransaction {
            val challenge = Challenges.select { Challenges.id eq challengeId }
                .firstOrNull() ?: return@dbTransaction null
            
            val participantCount = ChallengeParticipants.select {
                ChallengeParticipants.challengeId eq challengeId
            }.count().toInt()
            
            val tagsString = challenge[Challenges.tags]
            val tagsList = if (tagsString.isNullOrBlank()) {
                emptyList()
            } else {
                tagsString.split(",").map { it.trim() }.filter { it.isNotBlank() }
            }
            ChallengeDetail(
                id = challenge[Challenges.id],
                title = challenge[Challenges.title],
                description = challenge[Challenges.description],
                reward = challenge[Challenges.reward],
                prize = challenge[Challenges.prize],
                rules = challenge[Challenges.rules],
                displayType = challenge[Challenges.displayType],
                tags = tagsList,
                sponsor = challenge[Challenges.sponsor],
                startTime = challenge[Challenges.startTime].toString(),
                endTime = challenge[Challenges.endTime].toString(),
                thumbnail = challenge[Challenges.thumbnail],
                challengeType = challenge[Challenges.challengeType],
                packageNames = challenge[Challenges.packageNames],
                isActive = challenge[Challenges.isActive],
                participantCount = participantCount,
                createdAt = challenge[Challenges.createdAt].toString()
            )
        }
    }
    
    /**
     * Submit challenge participant stats
     */
    fun submitChallengeStats(
        userId: String,
        challengeId: Long,
        appName: String,
        packageName: String,
        startSyncTime: Instant,
        endSyncTime: Instant,
        duration: Long
    ) {
        dbTransaction {
            ChallengeParticipantStats.insert {
                it[ChallengeParticipantStats.challengeId] = challengeId
                it[ChallengeParticipantStats.userId] = userId
                it[ChallengeParticipantStats.appName] = appName
                it[ChallengeParticipantStats.packageName] = packageName
                it[ChallengeParticipantStats.startSyncTime] = startSyncTime
                it[ChallengeParticipantStats.endSyncTime] = endSyncTime
                it[ChallengeParticipantStats.duration] = duration
            }
        }
    }
    
    /**
     * Get challenge rankings
     * For LESS_SCREENTIME: rank by total duration ascending (lower is better)
     * For MORE_SCREENTIME: rank by total duration descending (higher is better)
     * Includes all participants, even those with no stats (0 duration)
     * Optimized: uses SQL aggregation and minimizes memory usage
     */
    fun getChallengeRankings(
        challengeId: Long,
        challengeType: String,
        limit: Int = 10
    ): List<Pair<String, Long>> {
        return dbTransaction {
            val durationSum = ChallengeParticipantStats.duration.sum()
            
            // Get aggregated stats with database-level sorting (only users with stats)
            val statsWithSorting = ChallengeParticipantStats
                .slice(
                    ChallengeParticipantStats.userId,
                    durationSum
                )
                .select {
                    ChallengeParticipantStats.challengeId eq challengeId
                }
                .groupBy(ChallengeParticipantStats.userId)
                .let { query ->
                    if (challengeType == "LESS_SCREENTIME") {
                        query.orderBy(durationSum to SortOrder.ASC)
                    } else {
                        query.orderBy(durationSum to SortOrder.DESC)
                    }
                }
            
            val statsMap = statsWithSorting.associate { row ->
                val userId = row[ChallengeParticipantStats.userId]
                val totalDuration = row[durationSum] ?: 0L
                userId to totalDuration
            }
            
            // Get participant user IDs (lightweight - just IDs)
            val participantUserIds = ChallengeParticipants
                .slice(ChallengeParticipants.userId)
                .select {
                    ChallengeParticipants.challengeId eq challengeId
                }
                .map { it[ChallengeParticipants.userId] }
            
            // Create rankings with 0 for users without stats
            val allRankings = participantUserIds.map { userId ->
                userId to (statsMap[userId] ?: 0L)
            }
            
            // Sort (users with 0 will be at top for LESS_SCREENTIME, bottom for MORE_SCREENTIME)
            val sorted = if (challengeType == "LESS_SCREENTIME") {
                allRankings.sortedBy { it.second }
            } else {
                allRankings.sortedByDescending { it.second }
            }
            
            sorted.take(limit)
        }
    }
    
    /**
     * Get user's total duration for a challenge
     */
    fun getUserChallengeDuration(userId: String, challengeId: Long): Long {
        return dbTransaction {
            ChallengeParticipantStats.select {
                (ChallengeParticipantStats.challengeId eq challengeId) and
                (ChallengeParticipantStats.userId eq userId)
            }.sumOf { it[ChallengeParticipantStats.duration] }
        }
    }
    
    /**
     * Get user's rank in a challenge
     * Returns null if user hasn't joined the challenge
     * Includes all participants, even those with no stats (0 duration)
     * Optimized to reuse ranking calculation logic
     */
    fun getUserRank(userId: String, challengeId: Long, challengeType: String): Int? {
        return dbTransaction {
            // Check if user has joined the challenge
            val hasJoined = ChallengeParticipants.select {
                (ChallengeParticipants.challengeId eq challengeId) and
                (ChallengeParticipants.userId eq userId)
            }.count() > 0
            
            if (!hasJoined) return@dbTransaction null
            
            // Get user's duration (optimized single query)
            val userDuration = ChallengeParticipantStats
                .slice(ChallengeParticipantStats.duration.sum())
                .select {
                    (ChallengeParticipantStats.challengeId eq challengeId) and
                    (ChallengeParticipantStats.userId eq userId)
                }
                .firstOrNull()
                ?.let { it[ChallengeParticipantStats.duration.sum()] ?: 0L }
                ?: 0L
            
            // Count how many users have better (or equal) duration
            // For LESS_SCREENTIME: count users with duration <= userDuration
            // For MORE_SCREENTIME: count users with duration >= userDuration
            val participantCount = ChallengeParticipants.select {
                ChallengeParticipants.challengeId eq challengeId
            }.count()
            
            if (participantCount == 0L) return@dbTransaction null
            
            // Get aggregated durations for all participants
            val allDurations = ChallengeParticipantStats
                .slice(
                    ChallengeParticipantStats.userId,
                    ChallengeParticipantStats.duration.sum()
                )
                .select {
                    ChallengeParticipantStats.challengeId eq challengeId
                }
                .groupBy(ChallengeParticipantStats.userId)
                .associate { row ->
                    val uid = row[ChallengeParticipantStats.userId]
                    val duration = row[ChallengeParticipantStats.duration.sum()] ?: 0L
                    uid to duration
                }
            
            // Get all participant IDs
            val allParticipants = ChallengeParticipants
                .slice(ChallengeParticipants.userId)
                .select {
                    ChallengeParticipants.challengeId eq challengeId
                }
                .map { it[ChallengeParticipants.userId] }
            
            // Create full ranking
            val allRankings = allParticipants.map { uid ->
                uid to (allDurations[uid] ?: 0L)
            }
            
            // Sort and find rank
            val sorted = if (challengeType == "LESS_SCREENTIME") {
                allRankings.sortedBy { it.second }
            } else {
                allRankings.sortedByDescending { it.second }
            }
            
            sorted.indexOfFirst { it.first == userId }.takeIf { it >= 0 }?.plus(1)
        }
    }
    
    /**
     * Get distinct app count for a user in a challenge
     */
    fun getUserAppCount(userId: String, challengeId: Long): Int {
        return dbTransaction {
            ChallengeParticipantStats.select {
                (ChallengeParticipantStats.challengeId eq challengeId) and
                (ChallengeParticipantStats.userId eq userId)
            }
            .map { it[ChallengeParticipantStats.packageName] }
            .distinct()
            .count()
        }
    }
    
    /**
     * Batch get app counts for multiple users in a challenge (optimized)
     * Returns a map of userId -> appCount
     * Uses SQL aggregation for better performance
     */
    fun getBatchUserAppCounts(userIds: Set<String>, challengeId: Long): Map<String, Int> {
        if (userIds.isEmpty()) return emptyMap()
        
        return dbTransaction {
            // Use SQL aggregation with COUNT(DISTINCT) for better performance
            val packageNameColumn = ChallengeParticipantStats.packageName
            val appCounts = ChallengeParticipantStats
                .slice(
                    ChallengeParticipantStats.userId,
                    packageNameColumn.countDistinct()
                )
                .select {
                    (ChallengeParticipantStats.challengeId eq challengeId) and
                    (ChallengeParticipantStats.userId inList userIds.toList())
                }
                .groupBy(ChallengeParticipantStats.userId)
                .associate { row ->
                    val userId = row[ChallengeParticipantStats.userId]
                    val appCount = row[packageNameColumn.countDistinct()].toInt()
                    userId to appCount
                }
            
            // Include users with 0 app count (users who haven't submitted stats)
            userIds.associateWith { userId ->
                appCounts[userId] ?: 0
            }
        }
    }
    
    /**
     * Get total participant count for a challenge
     */
    fun getParticipantCount(challengeId: Long): Int {
        return dbTransaction {
            ChallengeParticipants.select {
                ChallengeParticipants.challengeId eq challengeId
            }.count().toInt()
        }
    }
    
    /**
     * Get last sync time for a user in a challenge
     * Returns the most recent endSyncTime from challenge_participant_stats
     */
    fun getLastSyncTime(userId: String, challengeId: Long): String? {
        return dbTransaction {
            ChallengeParticipantStats.select {
                (ChallengeParticipantStats.challengeId eq challengeId) and
                (ChallengeParticipantStats.userId eq userId)
            }
            .orderBy(ChallengeParticipantStats.endSyncTime to SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.let { it[ChallengeParticipantStats.endSyncTime].toString() }
        }
    }
    
    /**
     * Check if user has submitted any stats for a challenge
     */
    fun hasUserSubmittedStats(userId: String, challengeId: Long): Boolean {
        return dbTransaction {
            ChallengeParticipantStats.select {
                (ChallengeParticipantStats.challengeId eq challengeId) and
                (ChallengeParticipantStats.userId eq userId)
            }.count() > 0
        }
    }
}

