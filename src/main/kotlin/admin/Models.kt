package com.apptime.code.admin

import kotlinx.serialization.Serializable

@Serializable
data class AdminStatsResponse(
    val users: UserStats,
    val challenges: ChallengeStats,
    val usage: UsageStats,
    val focus: FocusStats,
    val leaderboard: LeaderboardStats,
    val system: SystemStats
)

@Serializable
data class UserStats(
    val totalUsers: Long,
    val usersWithUsername: Long,
    val activeUsersLast24h: Long,
    val activeUsersLast7d: Long,
    val usersWithTOTP: Long,
    val recentRegistrations: List<UserSummary>
)

@Serializable
data class UserSummary(
    val userId: String,
    val username: String?,
    val deviceId: String,
    val deviceModel: String?,
    val createdAt: String,
    val lastSyncTime: String?
)

@Serializable
data class ChallengeStats(
    val totalChallenges: Long,
    val activeChallenges: Long,
    val totalParticipants: Long,
    val activeParticipants: Long,
    val recentChallenges: List<ChallengeSummary>
)

@Serializable
data class ChallengeSummary(
    val id: Long,
    val title: String,
    val challengeType: String,
    val startTime: String,
    val endTime: String,
    val participantCount: Long,
    val isActive: Boolean
)

@Serializable
data class UsageStats(
    val totalEvents: Long,
    val eventsLast24h: Long,
    val eventsLast7d: Long,
    val uniqueUsersWithData: Int,
    val topApps: List<AppUsageSummary?>
)

@Serializable
data class AppUsageSummary(
    val packageName: String,
    val appName: String,
    val eventCount: Long,
    val uniqueUsers: Long
)

@Serializable
data class FocusStats(
    val totalSessions: Long,
    val sessionsLast24h: Long,
    val sessionsLast7d: Long,
    val totalFocusTime: Long, // milliseconds
    val averageSessionDuration: Long // milliseconds
)

@Serializable
data class LeaderboardStats(
    val dailyEntries: Long,
    val weeklyEntries: Long,
    val topUsers: List<LeaderboardUser>
)

@Serializable
data class LeaderboardUser(
    val userId: String,
    val username: String?,
    val score: Long,
    val rank: Long
)

@Serializable
data class SystemStats(
    val databaseConnected: Boolean,
    val serverTime: String,
    val timezone: String
)

