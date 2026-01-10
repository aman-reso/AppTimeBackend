package com.apptime.code.challenges

import kotlinx.serialization.Serializable

/**
 * Challenge type enum
 */
enum class ChallengeType {
    LESS_SCREENTIME,  // Lower screen time is better (ranked ascending)
    MORE_SCREENTIME   // Higher screen time is better (ranked descending)
}

/**
 * Challenge display type enum - predefined types for challenges
 */
@Serializable
enum class ChallengeDisplayType {
    SPECIAL,      // Special featured challenges
    TRENDING,     // Currently trending challenges
    QUICK_JOIN,   // Quick join challenges (short duration, easy to start)
    FEATURE       // Featured challenges
}

/**
 * Challenge data model
 */
@Serializable
data class Challenge(
    val id: Long,
    val title: String,
    val description: String? = null,
    val reward: String? = null,
    val startTime: String, // ISO 8601 format
    val endTime: String,   // ISO 8601 format
    val thumbnail: String? = null,
    val challengeType: String, // "LESS_SCREENTIME" or "MORE_SCREENTIME"
    val variant: String? = null, // Challenge varient (e.g., "varient1", "varient2", "varient3")
    val isActive: Boolean = true,
    val createdAt: String? = null
)

/**
 * Active challenge response (simplified for listing)
 */
@Serializable
data class ActiveChallenge(
    val id: Long,
    val title: String,
    val description: String? = null,
    val reward: String? = null,
    val prize: String? = null, // HTML string with rank-based prizes
    val rules: String? = null, // HTML string with challenge rules
    val displayType: String? = null, // Challenge display type (SPECIAL, TRENDING, QUICK_JOIN, FEATURE)
    val tags: List<String> = emptyList(), // Challenge tags (browser, study, gaming, social media, etc.)
    val sponsor: String? = null, // Challenge sponsor name
    val startTime: String,
    val endTime: String,
    val thumbnail: String? = null,
    val packageNames: String? = null, // Comma-separated package names
    val scheme: String? = null, // Color scheme for the challenge (e.g., "blue", "purple", "green")
    val variant: String? = null, // Challenge varient (e.g., "varient1", "varient2", "varient3")
    val participantCount: Int = 0, // Number of participants who joined this challenge
    val hasJoined: Boolean = false
)

/**
 * Response for getting all active challenges
 */
@Serializable
data class ActiveChallengesResponse(
    val challenges: List<ActiveChallenge>
)

/**
 * Request to join/register for a challenge
 */
@Serializable
data class JoinChallengeRequest(
    val challengeId: Long
)

/**
 * Response for joining a challenge
 */
@Serializable
data class JoinChallengeResponse(
    val challengeId: Long,
    val userId: String,
    val joinedAt: String,
    val message: String
)

/**
 * User challenge (includes past challenges)
 */
@Serializable
data class UserChallenge(
    val id: Long,
    val title: String,
    val description: String? = null,
    val reward: String? = null,
    val startTime: String,
    val endTime: String,
    val thumbnail: String? = null,
    val challengeType: String,
    val isActive: Boolean,
    val joinedAt: String,
    val isPast: Boolean, // true if challenge has ended
    val packageNames: String? = null, // Comma-separated package names from challenge
    val lastSyncTime: String? = null // ISO 8601 format - most recent endSyncTime from stats, null if no stats submitted
)

/**
 * Response for getting all user challenges
 */
@Serializable
data class UserChallengesResponse(
    val challenges: List<UserChallenge>
)

/**
 * Challenge detail with participant count
 */
@Serializable
data class ChallengeDetail(
    val id: Long,
    val title: String,
    val description: String? = null,
    val reward: String? = null,
    val prize: String? = null, // HTML string with rank-based prizes
    val rules: String? = null, // HTML string with challenge rules
    val displayType: String? = null, // Challenge display type (SPECIAL, TRENDING, QUICK_JOIN, FEATURE)
    val tags: List<String> = emptyList(), // Challenge tags (browser, study, gaming, social media, etc.)
    val sponsor: String? = null, // Challenge sponsor name
    val startTime: String,
    val endTime: String,
    val thumbnail: String? = null,
    val challengeType: String,
    val packageNames: String? = null, // Comma-separated package names
    val scheme: String? = null, // Color scheme for the challenge (e.g., "blue", "purple", "green")
    val variant: String? = null, // Challenge varient (e.g., "varient1", "varient2", "varient3")
    val isActive: Boolean,
    val participantCount: Int,
    val hasJoined: Boolean = false, // Whether the current user has joined this challenge
    val createdAt: String? = null
)

/**
 * Request to submit challenge participant stats
 */
@Serializable
data class ChallengeStatsSubmissionRequest(
    val challengeId: Long,
    val appName: String,
    val packageName: String,
    val startSyncTime: String, // ISO 8601 format
    val endSyncTime: String,   // ISO 8601 format
    val duration: Long // milliseconds
)

/**
 * Batch request to submit multiple challenge stats
 */
@Serializable
data class BatchChallengeStatsSubmissionRequest(
    val challengeId: Long,
    val stats: List<ChallengeStatsSubmissionRequest>
)

/**
 * Response for submitting challenge stats
 */
@Serializable
data class ChallengeStatsSubmissionResponse(
    val submitted: Int,
    val totalDuration: Long
)

/**
 * Challenge participant rank entry
 */
@Serializable
data class ChallengeRankEntry(
    val rank: Int,
    val userId: String,
    val totalDuration: Long, // milliseconds
    val appCount: Int // number of different apps tracked
)

/**
 * Response for challenge ranking
 */
@Serializable
data class ChallengeRankResponse(
    val challengeId: Long,
    val challengeTitle: String,
    val challengeType: String,
    val rankings: List<ChallengeRankEntry>,
    val userRank: ChallengeRankEntry? = null, // Current user's rank if they're participating
    val totalParticipants: Int
)

/**
 * Response for last sync time
 */
@Serializable
data class ChallengeLastSyncResponse(
    val challengeId: Long,
    val userId: String,
    val lastSyncTime: String?, // ISO 8601 format, null if no stats submitted yet
    val hasStats: Boolean // true if user has submitted any stats for this challenge
)

/**
 * Response for challenge stats sync operation
 */
@Serializable
data class ChallengeStatsSyncResponse(
    val message: String,
    val eventsProcessed: Int,
    val challengesProcessed: Int,
    val statsCreated: Int,
    val usersUpdated: Int
)

