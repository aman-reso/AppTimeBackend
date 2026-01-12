package com.apptime.code.leaderboard

import kotlinx.serialization.Serializable

@Serializable
data class LeaderboardEntry(
    val userId: String,
    val username: String? = null,
    val name: String? = null,
    val avatar: String? = null,
    val totalScreenTime: Long, // milliseconds
    val rank: Int
)

@Serializable
data class LeaderboardResponse(
    val period: String, // "daily", "weekly", "monthly"
    val periodDate: String,
    val entries: List<LeaderboardEntry>,
    val userRank: Int? = null,
    val totalUsers: Int
)

@Serializable
data class LeaderboardSyncResponse(
    val message: String,
    val eventsProcessed: Int,
    val usersUpdated: Int,
    val statsCreated: Int,
    val statsUpdated: Int,
    val dateSynced: String? = null
)

@Serializable
data class UpdateLeaderboardStatsRequest(
    val period: String, // "daily" only - weekly and monthly are automatically updated from daily stats
    val periodDate: String, // YYYY-MM-DD format for daily
    val totalScreenTime: Long, // milliseconds
    val replace: Boolean = false // If true, replaces existing value. If false, adds to existing value.
)

@Serializable
data class UpdateLeaderboardStatsResponse(
    val success: Boolean,
    val message: String,
    val period: String,
    val periodDate: String,
    val totalScreenTime: Long,
    val action: String // "created" or "updated"
)

@Serializable
data class UsernameDatePair(
    val username: String,
    val date: String // YYYY-MM-DD format
)

@Serializable
data class GetScreenTimeByUsernamesRequest(
    val users: List<UsernameDatePair>
)

@Serializable
data class UserScreenTime(
    val username: String,
    val date: String, // YYYY-MM-DD format
    val screenTime: Long? = null // milliseconds
)

@Serializable
data class GetScreenTimeByUsernamesResponse(
    val users: List<UserScreenTime>
)

