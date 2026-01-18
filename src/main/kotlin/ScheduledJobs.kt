package com.apptime.code

import com.apptime.code.challenges.ChallengeRepository
import com.apptime.code.challenges.ChallengeService
import com.apptime.code.leaderboard.LeaderboardRepository
import com.apptime.code.leaderboard.LeaderboardService
import com.apptime.code.rewards.RewardRepository
import com.apptime.code.rewards.RewardService
import io.ktor.server.application.*
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

/**
 * Scheduled jobs for the application
 */
class ScheduledJobs(
    private val leaderboardService: LeaderboardService,
    private val challengeService: ChallengeService,
    private val rewardService: RewardService
) {
    private val logger = LoggerFactory.getLogger(ScheduledJobs::class.java)
    private var leaderboardSyncJob: Job? = null
    private var challengeStatsSyncJob: Job? = null
    private var challengeRewardsJob: Job? = null

    /**
     * Start the leaderboard sync job that runs every 10 minutes
     */
    fun startLeaderboardSyncJob(scope: CoroutineScope) {
        logger.info("Starting leaderboard sync job - will run every 10 minutes")
        
        leaderboardSyncJob = scope.launch {
            while (isActive) {
                try {
                    logger.info("Running scheduled leaderboard sync...")
                    val result = leaderboardService.syncFromAppUsageEvents(null)
                    logger.info("Leaderboard sync completed: ${result.message}. " +
                            "Events processed: ${result.eventsProcessed}, " +
                            "Stats updated: ${result.statsUpdated}")
                } catch (e: Exception) {
                    logger.error("Error during scheduled leaderboard sync: ${e.message}", e)
                }
                
                // Wait 10 minutes (600,000 milliseconds) before next run
                delay(10 * 60 * 1000L)
            }
        }
    }
    
    /**
     * Start the challenge stats sync job that runs every 15 minutes
     */
    fun startChallengeStatsSyncJob(scope: CoroutineScope) {
        logger.info("Starting challenge stats sync job - will run every 15 minutes")
        
        challengeStatsSyncJob = scope.launch {
            while (isActive) {
                try {
                    logger.info("Running scheduled challenge stats sync...")
                    val result = challengeService.syncChallengeStatsFromAppUsageEvents(null)
                    logger.info("Challenge stats sync completed: ${result.message}. " +
                            "Events processed: ${result.eventsProcessed}, " +
                            "Challenges processed: ${result.challengesProcessed}, " +
                            "Stats created: ${result.statsCreated}, " +
                            "Users updated: ${result.usersUpdated}")
                } catch (e: Exception) {
                    logger.error("Error during scheduled challenge stats sync: ${e.message}", e)
                }
                
                // Wait 15 minutes (900,000 milliseconds) before next run
                delay(15 * 60 * 1000L)
            }
        }
    }

    /**
     * Start the challenge rewards job that runs every hour
     * Checks for ended challenges and awards coins to winners
     */
    fun startChallengeRewardsJob(scope: CoroutineScope) {
        logger.info("Starting challenge rewards job - will run every hour")
        
        challengeRewardsJob = scope.launch {
            while (isActive) {
                try {
                    logger.info("Running scheduled challenge rewards check...")
                    val challengeRepository = ChallengeRepository()
                    val recentlyEndedChallenges = challengeRepository.getRecentlyEndedChallenges()
                    
                    if (recentlyEndedChallenges.isEmpty()) {
                        logger.info("No recently ended challenges found")
                    } else {
                        logger.info("Found ${recentlyEndedChallenges.size} recently ended challenge(s)")
                        var totalRewardsAwarded = 0
                        
                        for (challengeId in recentlyEndedChallenges) {
                            try {
                                val response = rewardService.awardChallengeRewards(challengeId, topNRanks = 10)
                                totalRewardsAwarded += response.rewardsAwarded
                                logger.info("Challenge $challengeId: ${response.message}")
                            } catch (e: Exception) {
                                logger.error("Error awarding rewards for challenge $challengeId: ${e.message}", e)
                            }
                        }
                        
                        logger.info("Challenge rewards check completed. Total rewards awarded: $totalRewardsAwarded")
                    }
                } catch (e: Exception) {
                    logger.error("Error during scheduled challenge rewards check: ${e.message}", e)
                }
                
                // Wait 1 hour (3,600,000 milliseconds) before next run
                delay(60 * 60 * 1000L)
            }
        }
    }
    
    /**
     * Stop all scheduled jobs
     */
    fun stop() {
        logger.info("Stopping scheduled jobs...")
        leaderboardSyncJob?.cancel()
        leaderboardSyncJob = null
        challengeStatsSyncJob?.cancel()
        challengeStatsSyncJob = null
        challengeRewardsJob?.cancel()
        challengeRewardsJob = null
    }
}

/**
 * Configure and start scheduled jobs
 */
fun Application.configureScheduledJobs() {
    val leaderboardRepository = LeaderboardRepository()
    val leaderboardService = LeaderboardService(leaderboardRepository)
    val challengeRepository = ChallengeRepository()
    val challengeService = ChallengeService(challengeRepository)
    val rewardRepository = RewardRepository()
    val rewardService = RewardService(rewardRepository, challengeRepository)
    val scheduledJobs = ScheduledJobs(leaderboardService, challengeService, rewardService)
    
    // Start the leaderboard sync job
    // Application extends CoroutineScope in Ktor, so we can use 'this' directly
    //scheduledJobs.startLeaderboardSyncJob(this)
    
    // Start the challenge stats sync job
    //scheduledJobs.startChallengeStatsSyncJob(this)
    
    // Start the challenge rewards job (awards coins when challenges end)
    scheduledJobs.startChallengeRewardsJob(this)
    
    // Store reference to stop jobs when application shuts down
    environment.monitor.subscribe(ApplicationStopped) {
        scheduledJobs.stop()
    }
    
    log.info("Scheduled jobs configured and started")
}

