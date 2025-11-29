package com.apptime.code.admin

import com.apptime.code.challenges.ChallengeParticipants
import com.apptime.code.challenges.Challenges
import com.apptime.code.common.dbTransaction
import com.apptime.code.focus.FocusSessions
import com.apptime.code.users.Users
import usage.AppUsageEvents
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*

class StatsRepository {
    
    fun getUserStats(): UserStats {
        return dbTransaction {
            val now = Clock.System.now()
            val last24h = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - (24 * 60 * 60 * 1000))
            val last7d = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - (7 * 24 * 60 * 60 * 1000))
            
            val totalUsers = Users.selectAll().count()
            val usersWithUsername = Users.select { Users.username.isNotNull() }.count()
            val activeUsersLast24h = Users.select { 
                (Users.lastSyncTime.isNotNull()) and (Users.lastSyncTime greater last24h)
            }.count()
            val activeUsersLast7d = Users.select { 
                (Users.lastSyncTime.isNotNull()) and (Users.lastSyncTime greater last7d)
            }.count()
            val usersWithTOTP = Users.select { Users.totpEnabled eq true }.count()
            
            val recentRegistrations = Users.selectAll()
                .orderBy(Users.createdAt to SortOrder.DESC)
                .limit(10)
                .map { row ->
                    UserSummary(
                        userId = row[Users.userId],
                        username = row[Users.username],
                        deviceId = row[Users.deviceId],
                        deviceModel = row[Users.model],
                        createdAt = row[Users.createdAt].toString(),
                        lastSyncTime = row[Users.lastSyncTime]?.toString()
                    )
                }
            
            UserStats(
                totalUsers = totalUsers,
                usersWithUsername = usersWithUsername,
                activeUsersLast24h = activeUsersLast24h,
                activeUsersLast7d = activeUsersLast7d,
                usersWithTOTP = usersWithTOTP,
                recentRegistrations = recentRegistrations
            )
        }
    }
    
    fun getChallengeStats(): ChallengeStats {
        return dbTransaction {
            val now = Clock.System.now()
            
            val totalChallenges = Challenges.selectAll().count()
            val activeChallenges = Challenges.select { 
                (Challenges.isActive eq true) and (Challenges.endTime greater now)
            }.count()
            val totalParticipants = ChallengeParticipants.selectAll().count().toLong()
            
            val activeChallengeIds = Challenges.select { 
                (Challenges.isActive eq true) and (Challenges.endTime greater now)
            }.map { it[Challenges.id] }
            
            val activeParticipants = if (activeChallengeIds.isNotEmpty()) {
                ChallengeParticipants.select { 
                    ChallengeParticipants.challengeId inList activeChallengeIds
                }.count().toLong()
            } else {
                0L
            }
            
            val recentChallenges = Challenges.selectAll()
                .orderBy(Challenges.createdAt to SortOrder.DESC)
                .limit(10)
                .map { row ->
                    val challengeId = row[Challenges.id]
                    val participantCount = ChallengeParticipants.select { 
                        ChallengeParticipants.challengeId eq challengeId
                    }.count().toLong()
                    
                    ChallengeSummary(
                        id = challengeId,
                        title = row[Challenges.title],
                        challengeType = row[Challenges.challengeType],
                        startTime = row[Challenges.startTime].toString(),
                        endTime = row[Challenges.endTime].toString(),
                        participantCount = participantCount,
                        isActive = row[Challenges.isActive] && row[Challenges.endTime] > now
                    )
                }
            
            ChallengeStats(
                totalChallenges = totalChallenges,
                activeChallenges = activeChallenges,
                totalParticipants = totalParticipants,
                activeParticipants = activeParticipants,
                recentChallenges = recentChallenges
            )
        }
    }
    
    fun getUsageStats(): UsageStats {
        return dbTransaction {
            val now = Clock.System.now()
            val last24h = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - (24 * 60 * 60 * 1000))
            val last7d = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - (7 * 24 * 60 * 60 * 1000))
            
            val totalEvents = AppUsageEvents.selectAll().count()
            val eventsLast24h = AppUsageEvents.select { 
                AppUsageEvents.eventTimestamp greater last24h
            }.count()
            val eventsLast7d = AppUsageEvents.select { 
                AppUsageEvents.eventTimestamp greater last7d
            }.count()
            
            val uniqueUsersWithData = AppUsageEvents.slice(AppUsageEvents.userId)
                .selectAll()
                .distinct()
                .count()
            
            // Top apps by event count
            val topApps = AppUsageEvents
                .slice(
                    AppUsageEvents.packageName,
                    AppUsageEvents.appName,
                    AppUsageEvents.packageName.count(),
                    AppUsageEvents.userId.countDistinct()
                )
                .selectAll()
                .groupBy(AppUsageEvents.packageName, AppUsageEvents.appName)
                .orderBy(AppUsageEvents.packageName.count() to SortOrder.DESC)
                .limit(10)
                .mapNotNull { row ->
                    val appName = row[AppUsageEvents.appName]
                    if (appName != null) {
                        AppUsageSummary(
                            packageName = row[AppUsageEvents.packageName],
                            appName = appName,
                            eventCount = row[AppUsageEvents.packageName.count()],
                            uniqueUsers = row[AppUsageEvents.userId.countDistinct()]
                        )
                    } else {
                        null
                    }
                }
            
            UsageStats(
                totalEvents = totalEvents,
                eventsLast24h = eventsLast24h,
                eventsLast7d = eventsLast7d,
                uniqueUsersWithData = uniqueUsersWithData,
                topApps = topApps
            )
        }
    }
    
    fun getFocusStats(): FocusStats {
        return dbTransaction {
            val now = Clock.System.now()
            val last24h = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - (24 * 60 * 60 * 1000))
            val last7d = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - (7 * 24 * 60 * 60 * 1000))
            
            val totalSessions = FocusSessions.selectAll().count()
            val sessionsLast24h = FocusSessions.select { 
                FocusSessions.startTime greater last24h
            }.count()
            val sessionsLast7d = FocusSessions.select { 
                FocusSessions.startTime greater last7d
            }.count()
            
            val allSessions = FocusSessions.selectAll()
            val totalFocusTime = allSessions.sumOf { it[FocusSessions.focusDuration] }
            val averageSessionDuration = if (totalSessions > 0) {
                totalFocusTime / totalSessions
            } else {
                0L
            }
            
            FocusStats(
                totalSessions = totalSessions,
                sessionsLast24h = sessionsLast24h,
                sessionsLast7d = sessionsLast7d,
                totalFocusTime = totalFocusTime,
                averageSessionDuration = averageSessionDuration
            )
        }
    }
    
    fun getLeaderboardStats(): com.apptime.code.admin.LeaderboardStats {
        return dbTransaction {
            val dailyEntries = com.apptime.code.leaderboard.LeaderboardStats
                .select { com.apptime.code.leaderboard.LeaderboardStats.period eq "daily" }
                .count()
            val weeklyEntries = com.apptime.code.leaderboard.LeaderboardStats
                .select { com.apptime.code.leaderboard.LeaderboardStats.period eq "weekly" }
                .count()
            
            // Get top users from leaderboard (using totalScreenTime as score)
            val topUsers = com.apptime.code.leaderboard.LeaderboardStats
                .join(Users, JoinType.INNER, com.apptime.code.leaderboard.LeaderboardStats.userId, Users.userId)
                .slice(
                    com.apptime.code.leaderboard.LeaderboardStats.userId,
                    Users.username,
                    com.apptime.code.leaderboard.LeaderboardStats.totalScreenTime.sum()
                )
                .selectAll()
                .groupBy(com.apptime.code.leaderboard.LeaderboardStats.userId, Users.username)
                .orderBy(com.apptime.code.leaderboard.LeaderboardStats.totalScreenTime.sum() to SortOrder.DESC)
                .limit(10)
                .mapIndexed { index, row ->
                    LeaderboardUser(
                        userId = row[com.apptime.code.leaderboard.LeaderboardStats.userId],
                        username = row[Users.username],
                        score = row[com.apptime.code.leaderboard.LeaderboardStats.totalScreenTime.sum()] ?: 0L,
                        rank = (index + 1).toLong()
                    )
                }
            
            com.apptime.code.admin.LeaderboardStats(
                dailyEntries = dailyEntries,
                weeklyEntries = weeklyEntries,
                topUsers = topUsers
            )
        }
    }
}

