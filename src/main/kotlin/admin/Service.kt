package com.apptime.code.admin

import kotlinx.datetime.Clock
import java.util.*

class AdminService(private val repository: StatsRepository) {
    
    fun getAdminStats(): AdminStatsResponse {
        val userStats = repository.getUserStats()
        val challengeStats = repository.getChallengeStats()
        val usageStats = repository.getUsageStats()
        val focusStats = repository.getFocusStats()
        val leaderboardStats = repository.getLeaderboardStats()
        
        val systemStats = SystemStats(
            databaseConnected = true,
            serverTime = Clock.System.now().toString(),
            timezone = TimeZone.getDefault().id
        )
        
        return AdminStatsResponse(
            users = userStats,
            challenges = challengeStats,
            usage = usageStats,
            focus = focusStats,
            leaderboard = leaderboardStats,
            system = systemStats
        )
    }
}

