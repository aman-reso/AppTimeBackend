package com.apptime.code.challenges

import com.apptime.code.notifications.NotificationService
import kotlinx.datetime.Instant
import java.time.LocalDate

/**
 * Challenge service layer - handles business logic
 */
class ChallengeService(
    private val repository: ChallengeRepository,
    private val notificationService: NotificationService? = null
) {
    
    /**
     * Get all active challenges
     */
    suspend fun getActiveChallenges(userId: String? = null): ActiveChallengesResponse {
        val challenges = repository.getActiveChallenges(userId)
        return ActiveChallengesResponse(challenges = challenges)
    }
    
    /**
     * Join a challenge
     */
    suspend fun joinChallenge(userId: String, challengeId: Long): JoinChallengeResponse {
        // Check if challenge exists
        val challenge = repository.getChallengeById(challengeId)
            ?: throw IllegalArgumentException("Challenge not found")
        
        // Check if challenge is active
        if (!challenge.isActive) {
            throw IllegalArgumentException("Challenge is not active")
        }
        
        // Check if challenge has ended
        val now = kotlinx.datetime.Clock.System.now()
        val endTime = Instant.parse(challenge.endTime)
        if (endTime < now) {
            throw IllegalArgumentException("Challenge has already ended")
        }
        
        // Check if user has already joined
        if (repository.hasUserJoinedChallenge(userId, challengeId)) {
            throw IllegalArgumentException("User has already joined this challenge")
        }
        
        // Join the challenge
        val joinedAt = repository.joinChallenge(userId, challengeId)
        
        // Send notification
        notificationService?.sendChallengeJoinNotification(
            userId = userId,
            challengeTitle = challenge.title,
            challengeId = challengeId
        )
        
        return JoinChallengeResponse(
            challengeId = challengeId,
            userId = userId,
            joinedAt = joinedAt,
            message = "Successfully joined challenge: ${challenge.title}"
        )
    }
    
    /**
     * Get all challenges for a user (including past ones)
     */
    suspend fun getUserChallenges(userId: String): UserChallengesResponse {
        val challenges = repository.getUserChallenges(userId)
        return UserChallengesResponse(challenges = challenges)
    }
    
    /**
     * Get challenge detail with participant count
     * @param userId Optional user ID to check if user has joined the challenge
     */
    suspend fun getChallengeDetail(challengeId: Long, userId: String? = null): ChallengeDetail {
        return repository.getChallengeDetail(challengeId, userId)
            ?: throw IllegalArgumentException("Challenge not found")
    }
    
    /**
     * Submit challenge participant stats
     */
    suspend fun submitChallengeStats(
        userId: String,
        request: ChallengeStatsSubmissionRequest
    ) {
        // Validate that user has joined the challenge
        if (!repository.hasUserJoinedChallenge(userId, request.challengeId)) {
            throw IllegalArgumentException("User has not joined this challenge")
        }
        
        // Validate challenge exists
        repository.getChallengeById(request.challengeId)
            ?: throw IllegalArgumentException("Challenge not found")
        
        // Parse timestamps
        val startSyncTime = try {
            Instant.parse(request.startSyncTime)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid startSyncTime format. Use ISO 8601 format")
        }
        
        val endSyncTime = try {
            Instant.parse(request.endSyncTime)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid endSyncTime format. Use ISO 8601 format")
        }
        
        // Validate endTime is after startTime
        if (endSyncTime <= startSyncTime) {
            throw IllegalArgumentException("endSyncTime must be after startSyncTime")
        }
        
        // Validate duration is positive
        if (request.duration <= 0) {
            throw IllegalArgumentException("duration must be greater than 0")
        }
        
        // Validate duration doesn't exceed the time window
        // The duration represents actual app usage time within the sync window
        val syncWindowDuration = endSyncTime.toEpochMilliseconds() - startSyncTime.toEpochMilliseconds()
        if (request.duration > syncWindowDuration) {
            throw IllegalArgumentException("duration cannot exceed the time difference between startSyncTime and endSyncTime")
        }
        
        // Submit stats
        repository.submitChallengeStats(
            userId = userId,
            challengeId = request.challengeId,
            appName = request.appName,
            packageName = request.packageName,
            startSyncTime = startSyncTime,
            endSyncTime = endSyncTime,
            duration = request.duration
        )
    }
    
    /**
     * Submit batch challenge stats
     */
    suspend fun submitBatchChallengeStats(
        userId: String,
        challengeId: Long,
        stats: List<ChallengeStatsSubmissionRequest>
    ): ChallengeStatsSubmissionResponse {
        if (stats.isEmpty()) {
            throw IllegalArgumentException("At least one stat entry is required")
        }
        
        var totalDuration = 0L
        for (stat in stats) {
            // Ensure all stats belong to the same challenge
            if (stat.challengeId != challengeId) {
                throw IllegalArgumentException("All stats must belong to the same challenge")
            }
            
            submitChallengeStats(userId, stat)
            totalDuration += stat.duration
        }
        
        return ChallengeStatsSubmissionResponse(
            submitted = stats.size,
            totalDuration = totalDuration
        )
    }
    
    /**
     * Get last sync time for a user in a challenge
     */
    suspend fun getLastSyncTime(userId: String, challengeId: Long): ChallengeLastSyncResponse {
        // Validate that user has joined the challenge
        if (!repository.hasUserJoinedChallenge(userId, challengeId)) {
            throw IllegalArgumentException("User has not joined this challenge")
        }
        
        // Validate challenge exists
        repository.getChallengeById(challengeId)
            ?: throw IllegalArgumentException("Challenge not found")
        
        val lastSyncTime = repository.getLastSyncTime(userId, challengeId)
        val hasStats = repository.hasUserSubmittedStats(userId, challengeId)
        
        return ChallengeLastSyncResponse(
            challengeId = challengeId,
            userId = userId,
            lastSyncTime = lastSyncTime,
            hasStats = hasStats
        )
    }
    
    /**
     * Get challenge rankings (top 10)
     */
    suspend fun getChallengeRankings(
        challengeId: Long,
        userId: String? = null
    ): ChallengeRankResponse {
        val challenge = repository.getChallengeById(challengeId)
            ?: throw IllegalArgumentException("Challenge not found")
        
        // Get top 10 rankings
        val rankings = repository.getChallengeRankings(challengeId, challenge.challengeType, limit = 10)
        
        // Build rank entries with proper tie handling
        // Users with the same duration share the same rank
        val rankEntries = mutableListOf<ChallengeRankEntry>()
        var currentRank = 1
        var previousDuration: Long? = null
        
        for (index in rankings.indices) {
            val rankingEntry = rankings[index]
            val username = rankingEntry.username
            val totalDuration = rankingEntry.duration
            
            // Update rank when duration changes (ties share the same rank)
            if (previousDuration != null && totalDuration != previousDuration) {
                currentRank = index + 1
            }
            // Note: currentRank is already initialized to 1 for the first entry
            
            // Use userId from ranking entry to get appCount
            val appCount = repository.getUserAppCount(rankingEntry.userId, challengeId)
            rankEntries.add(
                ChallengeRankEntry(
                    rank = currentRank,
                    userId = username, // Use username (or userId fallback) for display
                    totalDuration = totalDuration,
                    appCount = appCount
                )
            )
            
            previousDuration = totalDuration
        }
        
        // Get user's rank if provided
        val userRank = userId?.let { uid ->
            val userDuration = repository.getUserChallengeDuration(uid, challengeId)
            if (userDuration > 0) {
                val userRankPosition = repository.getUserRank(uid, challengeId, challenge.challengeType)
                if (userRankPosition != null) {
                    val appCount = repository.getUserAppCount(uid, challengeId)
                    val username = repository.getUsername(uid)
                    ChallengeRankEntry(
                        rank = userRankPosition,
                        userId = username,
                        totalDuration = userDuration,
                        appCount = appCount
                    )
                } else {
                    null
                }
            } else {
                null
            }
        }
        
        val totalParticipants = repository.getParticipantCount(challengeId)
        
        return ChallengeRankResponse(
            challengeId = challengeId,
            challengeTitle = challenge.title,
            challengeType = challenge.challengeType,
            rankings = rankEntries,
            userRank = userRank,
            totalParticipants = totalParticipants
        )
    }
    
    /**
     * Sync challenge stats from app_usage_events to challenge_participant_stats
     * @param date Optional date in YYYY-MM-DD format. If not provided, syncs all events from active challenges
     */
    suspend fun syncChallengeStatsFromAppUsageEvents(date: String? = null): ChallengeStatsSyncResponse {
        val localDate = date?.let {
            try {
                LocalDate.parse(it, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid date format. Expected YYYY-MM-DD")
            }
        }
        return repository.syncChallengeStatsFromAppUsageEvents(localDate)
    }
}

