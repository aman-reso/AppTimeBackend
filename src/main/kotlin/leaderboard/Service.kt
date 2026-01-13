package com.apptime.code.leaderboard

import users.UserRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.*

/**
 * Leaderboard service - handles business logic for leaderboard operations
 */
class LeaderboardService(
    private val repository: LeaderboardRepository,
    private val userRepository: UserRepository = UserRepository()
) {
    
    /**
     * Get daily leaderboard
     * @param date Optional date in YYYY-MM-DD format. If not provided, uses today's date
     * @param currentUserId Optional current user ID to determine userRank
     */
    suspend fun getDailyLeaderboard(date: String? = null, currentUserId: String? = null): LeaderboardResponse {
        // Parse and validate date
        val periodDate = if (date != null) {
            try {
                // Validate date format
                LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                date
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid date format. Expected YYYY-MM-DD")
            }
        } else {
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        }
        
        return repository.getDailyLeaderboard(periodDate, currentUserId)
    }
    
    /**
     * Get weekly leaderboard
     * @param weekDate Optional week date in YYYY-WW format. If not provided, uses current week
     * @param currentUserId Optional current user ID to determine userRank
     */
    suspend fun getWeeklyLeaderboard(weekDate: String? = null, currentUserId: String? = null): LeaderboardResponse {
        // Parse and validate week date
        val periodDate = if (weekDate != null) {
            // Validate week date format (YYYY-WW)
            if (!weekDate.matches(Regex("\\d{4}-W\\d{2}"))) {
                throw IllegalArgumentException("Invalid week date format. Expected YYYY-WW (e.g., 2024-W01)")
            }
            weekDate
        } else {
            getCurrentWeekDate()
        }
        
        return repository.getWeeklyLeaderboard(periodDate, currentUserId)
    }
    
    /**
     * Get current week date in YYYY-WW format
     */
    private fun getCurrentWeekDate(): String {
        val date = LocalDate.now()
        val weekFields = WeekFields.of(Locale.getDefault())
        val week = date.get(weekFields.weekOfWeekBasedYear())
        val year = date.get(weekFields.weekBasedYear())
        return "${year}-W${String.format("%02d", week)}"
    }
    
    /**
     * Sync data from app_usage_events to leaderboardstats
     * @param date Optional date in YYYY-MM-DD format. If not provided, syncs all dates
     */
    suspend fun syncFromAppUsageEvents(date: String? = null): LeaderboardSyncResponse {
        val localDate = date?.let {
            try {
                LocalDate.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid date format. Expected YYYY-MM-DD")
            }
        }
        return repository.syncFromAppUsageEvents(localDate)
    }
    
    /**
     * Get monthly leaderboard
     * @param monthDate Optional month date in YYYY-MM format. If not provided, uses current month
     * @param currentUserId Optional current user ID to determine userRank
     */
    suspend fun getMonthlyLeaderboard(monthDate: String? = null, currentUserId: String? = null): LeaderboardResponse {
        // Parse and validate month date
        val periodDate = if (monthDate != null) {
            // Validate month date format (YYYY-MM)
            try {
                LocalDate.parse("${monthDate}-01", DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                monthDate
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid month date format. Expected YYYY-MM (e.g., 2024-01)")
            }
        } else {
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
        }
        
        return repository.getMonthlyLeaderboard(periodDate, currentUserId)
    }
    
    /**
     * Directly update leaderboard stats for a user
     * Only daily period is supported - weekly and monthly are automatically updated from daily stats
     * @param userId User ID
     * @param period Period type: "daily" only (weekly/monthly are automatically updated)
     * @param periodDate Period date: YYYY-MM-DD for daily
     * @param totalScreenTime Screen time in milliseconds
     * @param replace If true, replaces existing total. If false, adds to existing total.
     * @return UpdateLeaderboardStatsResponse with result
     */
    suspend fun updateLeaderboardStats(
        userId: String,
        period: String,
        periodDate: String,
        totalScreenTime: Long,
        replace: Boolean = false
    ): UpdateLeaderboardStatsResponse {
        // Only daily period is supported - weekly and monthly are automatically updated
        if (period != "daily") {
            throw IllegalArgumentException("Only 'daily' period is supported. Weekly and monthly stats are automatically updated from daily stats.")
        }
        
        // Validate periodDate format for daily
        val date = try {
            LocalDate.parse(periodDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid periodDate format. Expected YYYY-MM-DD (e.g., 2024-01-15)")
        }
        
        // Validate totalScreenTime
        if (totalScreenTime < 0) {
            throw IllegalArgumentException("totalScreenTime must be non-negative")
        }
        
        val (action, finalTotalScreenTime) = repository.updateLeaderboardStats(userId, period, periodDate, totalScreenTime, replace)
        
        return UpdateLeaderboardStatsResponse(
            success = true,
            message = "Leaderboard stats ${action} successfully. Weekly and monthly stats updated automatically.",
            period = period,
            periodDate = periodDate,
            totalScreenTime = finalTotalScreenTime,
            action = action
        )
    }
    
    /**
     * Get daily screen time for a list of username-date pairs
     * Returns daily screen time for each username for their specified date
     * Only returns data for usernames that have verified the authenticated user via TOTP
     * @param usernameDatePairs List of username-date pairs to query
     * @param authenticatedUserId The authenticated user's ID (who is requesting the data)
     * @return GetScreenTimeByUsernamesResponse with daily screentime data (only for verified users)
     */
    suspend fun getScreenTimeByUsernames(
        usernameDatePairs: List<UsernameDatePair>,
        authenticatedUserId: String
    ): GetScreenTimeByUsernamesResponse {
        if (usernameDatePairs.isEmpty()) {
            throw IllegalArgumentException("Username-date pairs list cannot be empty")
        }
        
        if (authenticatedUserId.isBlank()) {
            throw IllegalArgumentException("Authenticated user ID is required")
        }
        
        // Validate username-date pairs (remove blanks)
        val validPairs = usernameDatePairs.filter { 
            it.username.isNotBlank() && it.date.isNotBlank()
        }
        
        if (validPairs.isEmpty()) {
            throw IllegalArgumentException("No valid username-date pairs provided")
        }
        
        // Validate all date formats
        validPairs.forEach { pair ->
            try {
                LocalDate.parse(pair.date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid date format for username '${pair.username}'. Expected YYYY-MM-DD")
            }
        }
        
        // Get all unique usernames
        val uniqueUsernames = validPairs.map { it.username }.distinct()
        
        // Get user IDs for all usernames
        val usernameToUserIdMap = uniqueUsernames.associateWith { username ->
            userRepository.getUserIdByUsername(username)
        }.filterValues { it != null }.mapValues { it.value!! }
        
        // Filter to only include usernames that have verified the authenticated user
        // Check: has each username (as requestingUserId) verified the authenticated user (as targetUserId)?
        val verifiedUsernameDatePairs = validPairs.filter { pair ->
            val usernameUserId = usernameToUserIdMap[pair.username]
            if (usernameUserId != null) {
                // Check if this username has verified the authenticated user
                // requestingUserId = usernameUserId (they did the verification)
                // targetUserId = authenticatedUserId (they were verified)
                userRepository.hasValidTOTPVerificationSession(usernameUserId, authenticatedUserId)
            } else {
                false // User not found, exclude
            }
        }
        
        // Get screentime data only for verified users
        val users = if (verifiedUsernameDatePairs.isNotEmpty()) {
            repository.getScreenTimeByUsernames(verifiedUsernameDatePairs)
        } else {
            emptyList()
        }
        
        return GetScreenTimeByUsernamesResponse(users = users)
    }
}

