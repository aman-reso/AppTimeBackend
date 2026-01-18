package com.apptime.code.challenges

import com.apptime.code.common.dbTransaction
import com.apptime.code.users.Users
import org.jetbrains.exposed.sql.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import usage.AppUsageEvents
import java.time.LocalDate
import java.time.ZoneId

class ChallengeRepository {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Parse appdetail JSON string to List<AppDetail>
     */
    private fun parseAppDetail(appdetailJson: String?): List<AppDetail>? {
        if (appdetailJson.isNullOrBlank()) {
            return null
        }
        return try {
            json.decodeFromString<List<AppDetail>>(appdetailJson)
        } catch (e: Exception) {
            null
        }
    }
    
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
                    scheme = row[Challenges.colorScheme],
                    variant = row[Challenges.variant],
                    appdetail = parseAppDetail(row[Challenges.appdetail]),
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
                        variant = row[Challenges.variant],
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
            val challenges = (ChallengeParticipants innerJoin Challenges)
                .select { ChallengeParticipants.userId eq userId }
                .orderBy(Challenges.endTime to SortOrder.DESC)
                .map { row ->
                    row[Challenges.id] to row
                }
            
            // Get challenge IDs to fetch last sync times
            val challengeIds = challenges.map { it.first }
            
            // Get last sync times for all challenges in one query
            val lastSyncTimes = if (challengeIds.isNotEmpty()) {
                ChallengeParticipantStats.select {
                    (ChallengeParticipantStats.challengeId inList challengeIds) and
                    (ChallengeParticipantStats.userId eq userId)
                }
                .groupBy { it[ChallengeParticipantStats.challengeId] }
                .mapValues { (_, rows) ->
                    rows.maxByOrNull { it[ChallengeParticipantStats.endSyncTime] }
                        ?.get(ChallengeParticipantStats.endSyncTime)
                        ?.toString()
                }
            } else {
                emptyMap()
            }
            
            challenges.map { (challengeId, row) ->
                val endTime = row[Challenges.endTime]
                UserChallenge(
                    id = challengeId,
                    title = row[Challenges.title],
                    description = row[Challenges.description],
                    reward = row[Challenges.reward],
                    startTime = row[Challenges.startTime].toString(),
                    endTime = endTime.toString(),
                    thumbnail = row[Challenges.thumbnail],
                    challengeType = row[Challenges.challengeType],
                    isActive = row[Challenges.isActive],
                    joinedAt = row[ChallengeParticipants.joinedAt].toString(),
                    isPast = endTime < now,
                    packageNames = row[Challenges.packageNames],
                    lastSyncTime = lastSyncTimes[challengeId]
                )
            }
        }
    }
    
    /**
     * Get challenges that have ended recently (in the last 24 hours)
     * These are candidates for awarding rewards
     */
    fun getRecentlyEndedChallenges(): List<Long> {
        return dbTransaction {
            val now = Clock.System.now()
            // Get challenges that ended in the last 24 hours (86,400,000 milliseconds = 1 day)
            val oneDayAgo = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - (24 * 60 * 60 * 1000).toLong())
            
            Challenges.select {
                (Challenges.isActive eq true) and
                (Challenges.endTime lessEq now) and
                (Challenges.endTime greaterEq oneDayAgo)
            }
            .map { it[Challenges.id] }
        }
    }
    
    /**
     * Get challenge detail with participant count
     * @param userId Optional user ID to check if user has joined the challenge
     */
    fun getChallengeDetail(challengeId: Long, userId: String? = null): ChallengeDetail? {
        return dbTransaction {
            val challenge = Challenges.select { Challenges.id eq challengeId }
                .firstOrNull() ?: return@dbTransaction null
            
            val participantCount = ChallengeParticipants.select {
                ChallengeParticipants.challengeId eq challengeId
            }.count().toInt()
            
            // Check if user has joined the challenge
            val hasJoined = if (userId != null) {
                hasUserJoinedChallenge(userId, challengeId)
            } else {
                false
            }
            
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
                scheme = challenge[Challenges.colorScheme],
                variant = challenge[Challenges.variant],
                appdetail = parseAppDetail(challenge[Challenges.appdetail]),
                isActive = challenge[Challenges.isActive],
                participantCount = participantCount,
                hasJoined = hasJoined,
                createdAt = challenge[Challenges.createdAt].toString()
            )
        }
    }
    
    /**
     * Submit challenge participant stats
     * Updates existing row if matching userId, challengeId, packageName, and same day (based on startSyncTime) are found
     * Otherwise inserts a new row (new entry for each day)
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
            // Convert startSyncTime to date for day comparison
            val startDate = java.time.Instant.ofEpochMilli(startSyncTime.toEpochMilliseconds())
                .atZone(ZoneId.of("UTC"))
                .toLocalDate()
            
            // Calculate start and end of day in UTC for SQL comparison
            val dayStart = startDate.atStartOfDay(ZoneId.of("UTC")).toInstant()
            val dayEnd = dayStart.plusSeconds(86400) // 24 hours later
            val dayStartInstant = Instant.fromEpochMilliseconds(dayStart.toEpochMilli())
            val dayEndInstant = Instant.fromEpochMilliseconds(dayEnd.toEpochMilli())
            
            // Check if a record exists with matching userId, challengeId, packageName, and same day
            val existingRecord = ChallengeParticipantStats.select {
                (ChallengeParticipantStats.userId eq userId) and
                (ChallengeParticipantStats.challengeId eq challengeId) and
                (ChallengeParticipantStats.packageName eq packageName) and
                (ChallengeParticipantStats.startSyncTime greaterEq dayStartInstant) and
                (ChallengeParticipantStats.startSyncTime less dayEndInstant)
            }.orderBy(ChallengeParticipantStats.startSyncTime to SortOrder.DESC)
            .firstOrNull()

            if (existingRecord != null) {
                // Update existing record for the same day
                val existingStartSyncTime = existingRecord[ChallengeParticipantStats.startSyncTime]
                ChallengeParticipantStats.update({
                    (ChallengeParticipantStats.userId eq userId) and
                    (ChallengeParticipantStats.challengeId eq challengeId) and
                    (ChallengeParticipantStats.packageName eq packageName) and
                    (ChallengeParticipantStats.startSyncTime eq existingStartSyncTime)
                }) {
                    it[ChallengeParticipantStats.appName] = appName
                    it[ChallengeParticipantStats.endSyncTime] = endSyncTime
                    it[ChallengeParticipantStats.duration] = duration
                }
            } else {
                // Insert new record (new entry for each day)
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
    }
    
    /**
     * Data class for ranking entry with userId, username, and duration
     */
    data class RankingEntry(
        val userId: String,
        val username: String, // username or userId if username is null
        val duration: Long
    )
    
    /**
     * Get challenge rankings
     * For LESS_SCREENTIME: rank by total duration ascending (lower is better)
     * For MORE_SCREENTIME: rank by total duration descending (higher is better)
     * Handles ties: users with same duration share the same rank
     * Returns usernames instead of userIds (falls back to userId if username is null)
     */
    fun getChallengeRankings(
        challengeId: Long,
        challengeType: String,
        limit: Int = 10
    ): List<RankingEntry> {
        return dbTransaction {
            val stats = ChallengeParticipantStats.select {
                ChallengeParticipantStats.challengeId eq challengeId
            }
            
            // Group by userId and sum durations
            val userTotals = stats.groupBy { it[ChallengeParticipantStats.userId] }
                .mapValues { (_, rows) ->
                    rows.sumOf { it[ChallengeParticipantStats.duration] }
                }
            
            // Sort based on challenge type
            val sorted = if (challengeType == "LESS_SCREENTIME") {
                userTotals.toList().sortedBy { it.second } // Ascending (lower is better)
            } else {
                userTotals.toList().sortedByDescending { it.second } // Descending (higher is better)
            }
            
            // Get top users
            val topUsers = sorted.take(limit)
            
            // Fetch usernames for all userIds at once
            val userIds = topUsers.map { it.first }
            val usernameMap = if (userIds.isNotEmpty()) {
                Users.select { Users.userId inList userIds }
                    .associate { it[Users.userId] to it[Users.username] }
            } else {
                emptyMap()
            }
            
            // Return ranking entries with userId, username (or userId fallback), and duration
            topUsers.map { (userId, duration) ->
                val username = usernameMap[userId] ?: userId
                RankingEntry(userId = userId, username = username, duration = duration)
            }
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
     * Handles ties: users with same duration share the same rank
     */
    fun getUserRank(userId: String, challengeId: Long, challengeType: String): Int? {
        return dbTransaction {
            val allUserTotals = ChallengeParticipantStats.select {
                ChallengeParticipantStats.challengeId eq challengeId
            }
            .groupBy { it[ChallengeParticipantStats.userId] }
            .mapValues { (_, rows) ->
                rows.sumOf { it[ChallengeParticipantStats.duration] }
            }
            
            val userDuration = allUserTotals[userId] ?: return@dbTransaction null
            if (userDuration == 0L) return@dbTransaction null
            
            // Sort based on challenge type
            val sorted = if (challengeType == "LESS_SCREENTIME") {
                allUserTotals.toList().sortedBy { it.second }
            } else {
                allUserTotals.toList().sortedByDescending { it.second }
            }
            
            // Calculate rank with proper tie handling
            // Users with the same duration share the same rank
            var currentRank = 1
            var previousDuration: Long? = null
            
            for (index in sorted.indices) {
                val (uid, duration) = sorted[index]
                
                // Update rank when duration changes (ties share the same rank)
                if (previousDuration != null && duration != previousDuration) {
                    currentRank = index + 1
                }
                // Note: currentRank is already initialized to 1 for the first entry
                
                if (uid == userId) {
                    return@dbTransaction currentRank
                }
                
                previousDuration = duration
            }
            
            null // User not found in sorted list
        }
    }
    
    /**
     * Get username for a userId (falls back to userId if username is null)
     */
    fun getUsername(userId: String): String {
        return dbTransaction {
            Users.select { Users.userId eq userId }
                .firstOrNull()
                ?.get(Users.username)
                ?: userId
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
    
    /**
     * Sync challenge stats from app_usage_events to challenge_participant_stats
     * Only syncs for active challenges and participants who have joined
     * @param date Optional date to sync. If null, syncs all events from active challenges
     */
    fun syncChallengeStatsFromAppUsageEvents(date: LocalDate? = null): ChallengeStatsSyncResponse {
        return dbTransaction {
            val now = Clock.System.now()
            
            // Get all active challenges
            data class ChallengeInfo(
                val id: Long,
                val startTime: Instant,
                val endTime: Instant,
                val packageNames: Set<String>
            )
            
            val activeChallenges = Challenges.select {
                (Challenges.isActive eq true) and
                (Challenges.endTime greaterEq now)
            }.map { row ->
                val packageNamesStr = row[Challenges.packageNames]
                val packageNames = if (packageNamesStr.isNullOrBlank()) {
                    emptySet<String>()
                } else {
                    packageNamesStr.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                }
                
                ChallengeInfo(
                    id = row[Challenges.id],
                    startTime = row[Challenges.startTime],
                    endTime = row[Challenges.endTime],
                    packageNames = packageNames
                )
            }
            
            if (activeChallenges.isEmpty()) {
                return@dbTransaction ChallengeStatsSyncResponse(
                    message = "No active challenges found to sync",
                    eventsProcessed = 0,
                    challengesProcessed = 0,
                    statsCreated = 0,
                    usersUpdated = 0
                )
            }
            
            var eventsProcessed = 0
            var statsCreated = 0
            val usersUpdated = mutableSetOf<String>()
            val challengeIds = activeChallenges.map { it.id }.toSet()
            
            // Get all participants for active challenges
            val participants = ChallengeParticipants.select {
                ChallengeParticipants.challengeId inList challengeIds
            }.map { row ->
                Pair(row[ChallengeParticipants.challengeId], row[ChallengeParticipants.userId])
            }
            
            if (participants.isEmpty()) {
                return@dbTransaction ChallengeStatsSyncResponse(
                    message = "No participants found for active challenges",
                    eventsProcessed = 0,
                    challengesProcessed = activeChallenges.size,
                    statsCreated = 0,
                    usersUpdated = 0
                )
            }
            
            // Group participants by challenge
            val participantsByChallenge = participants.groupBy { it.first }
            
            // Process each challenge
            for (challenge in activeChallenges) {
                val challengeId = challenge.id
                val startTime = challenge.startTime
                val endTime = challenge.endTime
                val allowedPackageNames = challenge.packageNames
                val challengeParticipants = participantsByChallenge[challengeId] ?: continue
                val participantUserIds = challengeParticipants.map { it.second }.toSet()
                
                // Build query for app usage events
                val eventsQuery = if (date != null) {
                    // Sync for specific date - adjust UTC boundaries to match IST day boundaries
                    val istOffsetHours = 5
                    val istOffsetMinutes = 30
                    val istOffsetSeconds = istOffsetHours * 3600 + istOffsetMinutes * 60
                    
                    val startOfDay = date.atStartOfDay(ZoneId.of("UTC")).toInstant()
                        .minusSeconds(istOffsetSeconds.toLong())
                    val endOfDay = startOfDay.plusSeconds(86400)
                    
                    val dayStartInstant = kotlinx.datetime.Instant.fromEpochMilliseconds(startOfDay.toEpochMilli())
                    val dayEndInstant = kotlinx.datetime.Instant.fromEpochMilliseconds(endOfDay.toEpochMilli())
                    
                    // Ensure events are within both the day range and challenge time range
                    val effectiveStart = maxOf(startTime, dayStartInstant)
                    val effectiveEnd = minOf(endTime, dayEndInstant)
                    
                    if (effectiveStart >= effectiveEnd) continue
                    
                    // Build query with conditional package name filter
                    if (allowedPackageNames.isNotEmpty()) {
                        AppUsageEvents.select {
                            (AppUsageEvents.userId inList participantUserIds) and
                            (AppUsageEvents.duration.isNotNull()) and
                            (AppUsageEvents.eventTimestamp greaterEq effectiveStart) and
                            (AppUsageEvents.eventTimestamp less effectiveEnd) and
                            (AppUsageEvents.packageName inList allowedPackageNames)
                        }
                    } else {
                        AppUsageEvents.select {
                            (AppUsageEvents.userId inList participantUserIds) and
                            (AppUsageEvents.duration.isNotNull()) and
                            (AppUsageEvents.eventTimestamp greaterEq effectiveStart) and
                            (AppUsageEvents.eventTimestamp less effectiveEnd)
                        }
                    }
                } else {
                    // Sync all events within challenge time range
                    if (allowedPackageNames.isNotEmpty()) {
                        AppUsageEvents.select {
                            (AppUsageEvents.userId inList participantUserIds) and
                            (AppUsageEvents.duration.isNotNull()) and
                            (AppUsageEvents.eventTimestamp greaterEq startTime) and
                            (AppUsageEvents.eventTimestamp lessEq endTime) and
                            (AppUsageEvents.packageName inList allowedPackageNames)
                        }
                    } else {
                        AppUsageEvents.select {
                            (AppUsageEvents.userId inList participantUserIds) and
                            (AppUsageEvents.duration.isNotNull()) and
                            (AppUsageEvents.eventTimestamp greaterEq startTime) and
                            (AppUsageEvents.eventTimestamp lessEq endTime)
                        }
                    }
                }
                
                val events = eventsQuery.toList()
                eventsProcessed += events.size
                
                if (events.isEmpty()) continue
                
                // Group events by (userId, packageName, eventTimestamp) to aggregate durations
                // We'll create stats entries grouped by hour windows to match typical sync patterns
                val eventGroups = events.groupBy { event ->
                    val eventTime = event[AppUsageEvents.eventTimestamp]
                    val userId = event[AppUsageEvents.userId]
                    val packageName = event[AppUsageEvents.packageName]
                    
                    // Group by hour window for aggregation
                    val hourWindow = eventTime.toEpochMilliseconds() / (60 * 60 * 1000) // Hour timestamp
                    
                    Triple(userId, packageName, hourWindow)
                }
                
                // Get existing stats to avoid duplicates
                // Check for existing stats in the time range we're syncing
                val existingStats = ChallengeParticipantStats.select {
                    (ChallengeParticipantStats.challengeId eq challengeId) and
                    (ChallengeParticipantStats.userId inList participantUserIds)
                }.map { row ->
                    Triple(
                        row[ChallengeParticipantStats.userId],
                        row[ChallengeParticipantStats.packageName],
                        row[ChallengeParticipantStats.startSyncTime].toEpochMilliseconds() / (60 * 60 * 1000)
                    )
                }.toSet()
                
                // Create stats entries for each group
                for ((key, eventList) in eventGroups) {
                    val (userId, packageName, hourWindow) = key
                    
                    // Check if we already have stats for this hour window
                    if (key in existingStats) {
                        continue // Skip if already synced
                    }
                    
                    // Aggregate duration for this group
                    val totalDuration = eventList.sumOf { it[AppUsageEvents.duration] ?: 0L }
                    if (totalDuration <= 0) continue
                    
                    // Get app name from first event (they should all have the same app name for same package)
                    val appName = eventList.firstOrNull()?.let { it[AppUsageEvents.appName] } ?: packageName
                    
                    // Calculate time window (start and end of the hour)
                    val windowStartMs = hourWindow * 60 * 60 * 1000
                    val windowEndMs = windowStartMs + (60 * 60 * 1000)
                    val startSyncTime = Instant.fromEpochMilliseconds(windowStartMs)
                    val endSyncTime = Instant.fromEpochMilliseconds(windowEndMs)
                    
                    // Ensure times are within challenge bounds
                    val effectiveStart = maxOf(startSyncTime, startTime)
                    val effectiveEnd = minOf(endSyncTime, endTime)
                    
                    if (effectiveStart >= effectiveEnd) continue
                    
                    // Insert the stat
                    ChallengeParticipantStats.insert {
                        it[ChallengeParticipantStats.challengeId] = challengeId
                        it[ChallengeParticipantStats.userId] = userId
                        it[ChallengeParticipantStats.appName] = appName
                        it[ChallengeParticipantStats.packageName] = packageName
                        it[ChallengeParticipantStats.startSyncTime] = effectiveStart
                        it[ChallengeParticipantStats.endSyncTime] = effectiveEnd
                        it[ChallengeParticipantStats.duration] = totalDuration
                    }
                    
                    statsCreated++
                    usersUpdated.add(userId)
                }
            }
            
            ChallengeStatsSyncResponse(
                message = "Challenge stats sync completed successfully",
                eventsProcessed = eventsProcessed,
                challengesProcessed = activeChallenges.size,
                statsCreated = statsCreated,
                usersUpdated = usersUpdated.size
            )
        }
    }
}

