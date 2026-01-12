package com.apptime.code.leaderboard

import com.apptime.code.common.dbTransaction
import com.apptime.code.users.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import usage.AppUsageEvents
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.*

class LeaderboardRepository {
    
    /**
     * Update leaderboard stats with focus time
     * Updates daily stats and automatically recalculates/updates weekly and monthly stats
     */
    fun updateLeaderboardWithFocusTime(
        userId: String,
        focusDuration: Long,
        date: LocalDate
    ) {
        dbTransaction {
            // Get username
            val username = Users.select { Users.userId eq userId }
                .firstOrNull()
                ?.get(Users.username)
            
            // Update daily stats
            updateDailyStats(userId, username, date, focusDuration)
            
            // Automatically update weekly and monthly stats by recalculating from daily stats
            updateWeeklyStatsFromDaily(userId, username, date)
            updateMonthlyStatsFromDaily(userId, username, date)
        }
    }
    
    private fun updateDailyStats(userId: String, username: String?, date: LocalDate, focusDuration: Long) {
        val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        
        val existing = LeaderboardStats.select {
            (LeaderboardStats.userId eq userId) and
            (LeaderboardStats.period eq "daily") and
            (LeaderboardStats.periodDate eq dateStr)
        }.firstOrNull()
        
        if (existing != null) {
            // Update existing
            LeaderboardStats.update({
                (LeaderboardStats.userId eq userId) and
                (LeaderboardStats.period eq "daily") and
                (LeaderboardStats.periodDate eq dateStr)
            }) {
                it[LeaderboardStats.totalScreenTime] = existing[LeaderboardStats.totalScreenTime] + focusDuration
                it[LeaderboardStats.updatedAt] = kotlinx.datetime.Clock.System.now()
                if (username != null) {
                    it[LeaderboardStats.username] = username
                }
            }
        } else {
            // Insert new
            LeaderboardStats.insert {
                it[LeaderboardStats.userId] = userId
                it[LeaderboardStats.username] = username
                it[LeaderboardStats.period] = "daily"
                it[LeaderboardStats.periodDate] = dateStr
                it[LeaderboardStats.totalScreenTime] = focusDuration
            }
        }
    }
    
    /**
     * Update weekly stats by recalculating from all daily stats in that week
     */
    private fun updateWeeklyStatsFromDaily(userId: String, username: String?, date: LocalDate) {
        val weekDate = getWeekDate(date)
        
        // Get all dates in this week
        val (weekStart, weekEnd) = parseWeekDate(weekDate)
        val datesInWeek = mutableListOf<String>()
        var currentDate = weekStart
        while (!currentDate.isAfter(weekEnd)) {
            datesInWeek.add(currentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
            currentDate = currentDate.plusDays(1)
        }
        
        // Sum all daily stats for this week
        val weeklyTotal = LeaderboardStats.select {
            (LeaderboardStats.userId eq userId) and
            (LeaderboardStats.period eq "daily") and
            (LeaderboardStats.periodDate inList datesInWeek)
        }.sumOf { it[LeaderboardStats.totalScreenTime] }
        
        // Update or create weekly stats
        updateOrCreateStats(userId, username, "weekly", weekDate, weeklyTotal, true)
    }
    
    /**
     * Update monthly stats by recalculating from all daily stats in that month
     */
    private fun updateMonthlyStatsFromDaily(userId: String, username: String?, date: LocalDate) {
        val monthDate = date.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        
        // Get all dates in this month
        val (monthStart, monthEnd) = parseMonthDate(monthDate)
        val datesInMonth = mutableListOf<String>()
        var currentDate = monthStart
        while (!currentDate.isAfter(monthEnd)) {
            datesInMonth.add(currentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
            currentDate = currentDate.plusDays(1)
        }
        
        // Sum all daily stats for this month
        val monthlyTotal = LeaderboardStats.select {
            (LeaderboardStats.userId eq userId) and
            (LeaderboardStats.period eq "daily") and
            (LeaderboardStats.periodDate inList datesInMonth)
        }.sumOf { it[LeaderboardStats.totalScreenTime] }
        
        // Update or create monthly stats
        updateOrCreateStats(userId, username, "monthly", monthDate, monthlyTotal, true)
    }
    
    private fun getWeekDate(date: LocalDate): String {
        val weekFields = WeekFields.of(Locale.getDefault())
        val week = date.get(weekFields.weekOfWeekBasedYear())
        val year = date.get(weekFields.weekBasedYear())
        return "${year}-W${String.format("%02d", week)}"
    }
    
    /**
     * Parse week date (YYYY-WW) and return the start and end dates of that week
     */
    private fun parseWeekDate(weekDate: String): Pair<LocalDate, LocalDate> {
        val parts = weekDate.split("-W")
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid week date format: $weekDate")
        }
        val year = parts[0].toInt()
        val week = parts[1].toInt()
        
        val weekFields = WeekFields.of(Locale.getDefault())
        
        // Find a date in the target week
        // Start from January 1st and find the week
        var date = LocalDate.of(year, 1, 1)
        var currentWeek = date.get(weekFields.weekOfWeekBasedYear())
        var currentYear = date.get(weekFields.weekBasedYear())
        
        // Adjust to the target week
        while (currentYear < year || (currentYear == year && currentWeek < week)) {
            date = date.plusWeeks(1)
            currentWeek = date.get(weekFields.weekOfWeekBasedYear())
            currentYear = date.get(weekFields.weekBasedYear())
        }
        while (currentYear > year || (currentYear == year && currentWeek > week)) {
            date = date.minusWeeks(1)
            currentWeek = date.get(weekFields.weekOfWeekBasedYear())
            currentYear = date.get(weekFields.weekBasedYear())
        }
        
        // Get the start of the week (Monday)
        val weekStart = date.with(weekFields.dayOfWeek(), 1)
        val weekEnd = weekStart.plusDays(6)
        
        return Pair(weekStart, weekEnd)
    }
    
    /**
     * Parse month date (YYYY-MM) and return the start and end dates of that month
     */
    private fun parseMonthDate(monthDate: String): Pair<LocalDate, LocalDate> {
        val parts = monthDate.split("-")
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid month date format: $monthDate")
        }
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        
        val monthStart = LocalDate.of(year, month, 1)
        val monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
        
        return Pair(monthStart, monthEnd)
    }
    
    /**
     * Sync data from app_usage_events to leaderboardstats
     * Creates/updates daily stats and automatically recalculates/updates weekly and monthly stats
     * @param date Optional date to sync. If null, syncs all dates from app_usage_events
     * @return LeaderboardSyncResponse with sync statistics
     */
    fun syncFromAppUsageEvents(date: LocalDate? = null): LeaderboardSyncResponse {
        return dbTransaction {
            // Get all events with duration (these represent actual screen time)
            val eventsQuery = if (date != null) {
                // Sync for specific date
                // Adjust UTC boundaries to match IST day boundaries (IST is UTC+5:30)
                val istOffsetHours = 5
                val istOffsetMinutes = 30
                val istOffsetSeconds = istOffsetHours * 3600 + istOffsetMinutes * 60
                
                val startOfDay = date.atStartOfDay(ZoneId.of("UTC")).toInstant()
                    .minusSeconds(istOffsetSeconds.toLong()) // Subtract IST offset to get IST midnight in UTC
                val endOfDay = startOfDay.plusSeconds(86400) // Add 24 hours (start of next day)
                val startInstant = kotlinx.datetime.Instant.fromEpochMilliseconds(startOfDay.toEpochMilli())
                val endInstant = kotlinx.datetime.Instant.fromEpochMilliseconds(endOfDay.toEpochMilli())
                
                AppUsageEvents.select {
                    (AppUsageEvents.duration.isNotNull()) and
                    (AppUsageEvents.eventTimestamp greaterEq startInstant) and
                    (AppUsageEvents.eventTimestamp less endInstant)
                }
            } else {
                // Sync all events
                AppUsageEvents.select {
                    AppUsageEvents.duration.isNotNull()
                }
            }
            
            val events = eventsQuery.toList()
            
            if (events.isEmpty()) {
                return@dbTransaction LeaderboardSyncResponse(
                    message = "No events found to sync",
                    eventsProcessed = 0,
                    usersUpdated = 0,
                    statsCreated = 0,
                    statsUpdated = 0,
                    dateSynced = date?.toString()
                )
            }
            
            // Group events by userId and date
            // Use IST timezone to get the correct date for grouping
            val istOffsetHours = 5
            val istOffsetMinutes = 30
            val istOffsetSeconds = istOffsetHours * 3600 + istOffsetMinutes * 60
            val istZone = ZoneId.of("Asia/Kolkata")
            
            val userDateStats = events.groupBy { event ->
                val eventTimestamp = event[AppUsageEvents.eventTimestamp]
                // Convert to IST to get the correct local date
                val localDate = java.time.Instant.ofEpochMilli(eventTimestamp.toEpochMilliseconds())
                    .atZone(istZone)
                    .toLocalDate()
                Pair(event[AppUsageEvents.userId], localDate)
            }.mapValues { (_, eventList) ->
                eventList.sumOf { it[AppUsageEvents.duration] ?: 0L }
            }
            
            var statsCreated = 0
            var statsUpdated = 0
            val usersUpdated = mutableSetOf<String>()
            
            // Group by daily stats
            val dailyStats = mutableMapOf<Pair<String, String>, Long>() // (userId, dateStr) -> totalTime
            val datesProcessed = mutableSetOf<LocalDate>() // Track unique dates to update weekly/monthly
            
            // Process each user-date combination
            userDateStats.forEach { (userDatePair, totalScreenTime) ->
                val (userId, eventDate) = userDatePair
                usersUpdated.add(userId)
                datesProcessed.add(eventDate)
                
                // Aggregate daily stats
                val dateStr = eventDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                dailyStats[Pair(userId, dateStr)] = 
                    dailyStats.getOrDefault(Pair(userId, dateStr), 0L) + totalScreenTime
            }
            
            // Get all usernames at once for efficiency
            val userIds = usersUpdated.toList()
            val usernameMap = Users.select { Users.userId inList userIds }
                .associate { it[Users.userId] to it[Users.username] }
            
            // Update daily stats
            dailyStats.forEach { (key, totalScreenTime) ->
                val (userId, dateStr) = key
                val username = usernameMap[userId]
                val result = updateOrCreateStats(userId, username, "daily", dateStr, totalScreenTime, true)
                if (result == "created") statsCreated++ else statsUpdated++
            }
            
            // Update weekly and monthly stats for all affected users and dates
            // Track which weeks and months we've already updated to avoid duplicates
            val weeksUpdated = mutableSetOf<Pair<String, String>>() // (userId, weekDate)
            val monthsUpdated = mutableSetOf<Pair<String, String>>() // (userId, monthDate)
            
            datesProcessed.forEach { date ->
                userIds.forEach { userId ->
                    val username = usernameMap[userId]
                    
                    // Update weekly stats
                    val weekDate = getWeekDate(date)
                    if (!weeksUpdated.contains(Pair(userId, weekDate))) {
                        updateWeeklyStatsFromDaily(userId, username, date)
                        weeksUpdated.add(Pair(userId, weekDate))
                        statsUpdated++ // Count as update (could be created or updated)
                    }
                    
                    // Update monthly stats
                    val monthDate = date.format(DateTimeFormatter.ofPattern("yyyy-MM"))
                    if (!monthsUpdated.contains(Pair(userId, monthDate))) {
                        updateMonthlyStatsFromDaily(userId, username, date)
                        monthsUpdated.add(Pair(userId, monthDate))
                        statsUpdated++ // Count as update (could be created or updated)
                    }
                }
            }
            
            LeaderboardSyncResponse(
                message = "Sync completed successfully",
                eventsProcessed = events.size,
                usersUpdated = usersUpdated.size,
                statsCreated = statsCreated,
                statsUpdated = statsUpdated,
                dateSynced = date?.toString()
            )
        }
    }
    
    /**
     * Directly update or create leaderboard stats entry
     * Only supports daily period - automatically updates weekly and monthly stats
     * @param userId User ID
     * @param period Period type: "daily" only
     * @param periodDate Period date: YYYY-MM-DD for daily
     * @param totalScreenTime Screen time in milliseconds
     * @param replace If true, replaces existing total. If false, adds to existing total.
     * @return Pair of (action, finalTotalScreenTime) where action is "created" or "updated"
     */
    fun updateLeaderboardStats(
        userId: String,
        period: String,
        periodDate: String,
        totalScreenTime: Long,
        replace: Boolean = false
    ): Pair<String, Long> {
        return dbTransaction {
            // Only daily period is supported
            if (period != "daily") {
                throw IllegalArgumentException("Only 'daily' period is supported")
            }
            
            // Parse date
            val date = LocalDate.parse(periodDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            
            // Get username
            val username = Users.select { Users.userId eq userId }
                .firstOrNull()
                ?.get(Users.username)
            
            // Update daily stats
            val action = updateOrCreateStats(userId, username, period, periodDate, totalScreenTime, replace)
            
            // Automatically update weekly and monthly stats
            updateWeeklyStatsFromDaily(userId, username, date)
            updateMonthlyStatsFromDaily(userId, username, date)
            
            // Get the final total screen time after update
            val finalEntry = LeaderboardStats.select {
                (LeaderboardStats.userId eq userId) and
                (LeaderboardStats.period eq period) and
                (LeaderboardStats.periodDate eq periodDate)
            }.firstOrNull()
            
            val finalTotal = finalEntry?.get(LeaderboardStats.totalScreenTime) ?: totalScreenTime
            
            Pair(action, finalTotal)
        }
    }
    
    /**
     * Update or create leaderboard stats entry
     * @param replace If true, replaces existing total. If false, adds to existing total.
     * @return "created" if new entry was created, "updated" if existing was updated
     */
    private fun updateOrCreateStats(
        userId: String,
        username: String?,
        period: String,
        periodDate: String,
        screenTime: Long,
        replace: Boolean = false
    ): String {
        // First, check if there are any existing entries (handle potential duplicates)
        val existingEntries = LeaderboardStats.select {
            (LeaderboardStats.userId eq userId) and
            (LeaderboardStats.period eq period) and
            (LeaderboardStats.periodDate eq periodDate)
        }.toList()
        
        // If multiple entries exist (duplicates), merge them first
        if (existingEntries.size > 1) {
            val totalTime = existingEntries.sumOf { it[LeaderboardStats.totalScreenTime] }
            val firstId = existingEntries.first()[LeaderboardStats.id]
            
            // Delete all duplicates except the first one
            val duplicateIds = existingEntries.drop(1).map { it[LeaderboardStats.id] }
            if (duplicateIds.isNotEmpty()) {
                LeaderboardStats.deleteWhere {
                    LeaderboardStats.id inList duplicateIds
                }
            }
            
            // Update the first entry with merged total
            val newTotal = if (replace) {
                screenTime
            } else {
                totalTime + screenTime
            }
            
            LeaderboardStats.update({ LeaderboardStats.id eq firstId }) {
                it[LeaderboardStats.totalScreenTime] = newTotal
                it[LeaderboardStats.updatedAt] = kotlinx.datetime.Clock.System.now()
                if (username != null) {
                    it[LeaderboardStats.username] = username
                }
            }
            return "updated"
        }
        
        val existing = existingEntries.firstOrNull()
        
        return if (existing != null) {
            // Update existing
            val newTotal = if (replace) {
                screenTime
            } else {
                existing[LeaderboardStats.totalScreenTime] + screenTime
            }
            
            LeaderboardStats.update({
                (LeaderboardStats.userId eq userId) and
                (LeaderboardStats.period eq period) and
                (LeaderboardStats.periodDate eq periodDate)
            }) {
                it[LeaderboardStats.totalScreenTime] = newTotal
                it[LeaderboardStats.updatedAt] = kotlinx.datetime.Clock.System.now()
                if (username != null) {
                    it[LeaderboardStats.username] = username
                }
            }
            "updated"
        } else {
            // Insert new - use insertIgnore to handle race conditions
            try {
                LeaderboardStats.insert {
                    it[LeaderboardStats.userId] = userId
                    it[LeaderboardStats.username] = username
                    it[LeaderboardStats.period] = period
                    it[LeaderboardStats.periodDate] = periodDate
                    it[LeaderboardStats.totalScreenTime] = screenTime
                }
                "created"
            } catch (e: Exception) {
                // If insert fails due to unique constraint, update instead
                val existingAfterInsert = LeaderboardStats.select {
                    (LeaderboardStats.userId eq userId) and
                    (LeaderboardStats.period eq period) and
                    (LeaderboardStats.periodDate eq periodDate)
                }.firstOrNull()
                
                if (existingAfterInsert != null) {
                    val newTotal = if (replace) {
                        screenTime
                    } else {
                        existingAfterInsert[LeaderboardStats.totalScreenTime] + screenTime
                    }
                    
                    LeaderboardStats.update({
                        (LeaderboardStats.userId eq userId) and
                        (LeaderboardStats.period eq period) and
                        (LeaderboardStats.periodDate eq periodDate)
                    }) {
                        it[LeaderboardStats.totalScreenTime] = newTotal
                        it[LeaderboardStats.updatedAt] = kotlinx.datetime.Clock.System.now()
                        if (username != null) {
                            it[LeaderboardStats.username] = username
                        }
                    }
                    "updated"
                } else {
                    throw e
                }
            }
        }
    }
    
    /**
     * Get daily leaderboard entries
     * @param date Date in YYYY-MM-DD format
     * @param currentUserId Optional current user ID to determine userRank
     * @return LeaderboardResponse with entries sorted by totalScreenTime descending
     */
    fun getDailyLeaderboard(date: String, currentUserId: String? = null): LeaderboardResponse {
        return dbTransaction {
            // Query leaderboard stats for the given date, joined with users table
            val entries = LeaderboardStats
                .join(Users, JoinType.INNER, LeaderboardStats.userId, Users.userId)
                .select {
                    (LeaderboardStats.period eq "daily") and
                    (LeaderboardStats.periodDate eq date)
                }
                .orderBy(LeaderboardStats.totalScreenTime to SortOrder.DESC)
                .mapIndexed { index, row ->
                    LeaderboardEntry(
                        userId = row[LeaderboardStats.userId],
                        username = row[LeaderboardStats.username] ?: row[Users.username],
                        name = row[Users.name],
                        avatar = null, // Avatar not stored in Users table
                        totalScreenTime = row[LeaderboardStats.totalScreenTime],
                        rank = index + 1
                    )
                }
            
            // Find current user's rank if userId is provided
            val userRank = currentUserId?.let { userId ->
                entries.find { it.userId == userId }?.rank
            }
            
            LeaderboardResponse(
                period = "daily",
                periodDate = date,
                entries = entries,
                userRank = userRank,
                totalUsers = entries.size
            )
        }
    }
    
    /**
     * Get weekly leaderboard entries
     * @param weekDate Week date in YYYY-WW format
     * @param currentUserId Optional current user ID to determine userRank
     * @return LeaderboardResponse with entries sorted by totalScreenTime descending
     */
    fun getWeeklyLeaderboard(weekDate: String, currentUserId: String? = null): LeaderboardResponse {
        return dbTransaction {
            // Query leaderboard stats for the given week, joined with users table
            val entries = LeaderboardStats
                .join(Users, JoinType.INNER, LeaderboardStats.userId, Users.userId)
                .select {
                    (LeaderboardStats.period eq "weekly") and
                    (LeaderboardStats.periodDate eq weekDate)
                }
                .orderBy(LeaderboardStats.totalScreenTime to SortOrder.DESC)
                .mapIndexed { index, row ->
                    LeaderboardEntry(
                        userId = row[LeaderboardStats.userId],
                        username = row[LeaderboardStats.username] ?: row[Users.username],
                        name = row[Users.name],
                        avatar = null, // Avatar not stored in Users table
                        totalScreenTime = row[LeaderboardStats.totalScreenTime],
                        rank = index + 1
                    )
                }
            
            // Find current user's rank if userId is provided
            val userRank = currentUserId?.let { userId ->
                entries.find { it.userId == userId }?.rank
            }
            
            LeaderboardResponse(
                period = "weekly",
                periodDate = weekDate,
                entries = entries,
                userRank = userRank,
                totalUsers = entries.size
            )
        }
    }
    
    /**
     * Get monthly leaderboard entries
     * @param monthDate Month date in YYYY-MM format
     * @param currentUserId Optional current user ID to determine userRank
     * @return LeaderboardResponse with entries sorted by totalScreenTime descending
     */
    fun getMonthlyLeaderboard(monthDate: String, currentUserId: String? = null): LeaderboardResponse {
        return dbTransaction {
            // Query leaderboard stats for the given month, joined with users table
            val entries = LeaderboardStats
                .join(Users, JoinType.INNER, LeaderboardStats.userId, Users.userId)
                .select {
                    (LeaderboardStats.period eq "monthly") and
                    (LeaderboardStats.periodDate eq monthDate)
                }
                .orderBy(LeaderboardStats.totalScreenTime to SortOrder.DESC)
                .mapIndexed { index, row ->
                    LeaderboardEntry(
                        userId = row[LeaderboardStats.userId],
                        username = row[LeaderboardStats.username] ?: row[Users.username],
                        name = row[Users.name],
                        avatar = null, // Avatar not stored in Users table
                        totalScreenTime = row[LeaderboardStats.totalScreenTime],
                        rank = index + 1
                    )
                }
            
            // Find current user's rank if userId is provided
            val userRank = currentUserId?.let { userId ->
                entries.find { it.userId == userId }?.rank
            }
            
            LeaderboardResponse(
                period = "monthly",
                periodDate = monthDate,
                entries = entries,
                userRank = userRank,
                totalUsers = entries.size
            )
        }
    }
    
    /**
     * Get daily screen time for a list of username-date pairs from leaderboard stats
     * Returns daily screen time for each username for their specified date
     * @param usernameDatePairs List of username-date pairs to query
     * @return List of UserScreenTime with daily screentime data
     */
    fun getScreenTimeByUsernames(usernameDatePairs: List<UsernameDatePair>): List<UserScreenTime> {
        if (usernameDatePairs.isEmpty()) {
            return emptyList()
        }
        
        return dbTransaction {
            // Validate all dates first
            val validatedPairs = usernameDatePairs.map { pair ->
                try {
                    LocalDate.parse(pair.date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    pair
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid date format for username '${pair.username}'. Expected YYYY-MM-DD")
                }
            }
            
            // Get unique usernames
            val uniqueUsernames = validatedPairs.map { it.username }.distinct()
            
            // Get user IDs and usernames map
            val usernameToUserIdMap = Users.select { Users.username inList uniqueUsernames }
                .associate { it[Users.username] to it[Users.userId] }
            
            val userIds = usernameToUserIdMap.values.toList()
            
            if (userIds.isEmpty()) {
                return@dbTransaction validatedPairs.map { pair ->
                    UserScreenTime(
                        username = pair.username,
                        date = pair.date,
                        screenTime = null
                    )
                }
            }
            
            // Get all unique dates
            val uniqueDates = validatedPairs.map { it.date }.distinct()
            
            // Get daily leaderboard stats for these users and dates
            val stats = LeaderboardStats.select {
                (LeaderboardStats.userId inList userIds) and
                (LeaderboardStats.period eq "daily") and
                (LeaderboardStats.periodDate inList uniqueDates)
            }
            
            // Group stats by userId and date
            val statsByUserAndDate = stats.groupBy { 
                Pair(it[LeaderboardStats.userId], it[LeaderboardStats.periodDate])
            }.mapValues { it.value.first()[LeaderboardStats.totalScreenTime] }
            
            // Build response for each username-date pair
            validatedPairs.map { pair ->
                val userId = usernameToUserIdMap[pair.username]
                val dailyScreentime = userId?.let { 
                    statsByUserAndDate[Pair(it, pair.date)]
                }
                
                UserScreenTime(
                    username = pair.username,
                    date = pair.date,
                    screenTime = dailyScreentime
                )
            }
        }
    }
}

