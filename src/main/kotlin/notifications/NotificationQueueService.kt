package com.apptime.code.notifications

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.logging.Logger

/**
 * Service for managing notification message queue
 * Uses in-memory queue with coroutines for async processing
 * Can be easily replaced with Redis Lists (LPUSH/RPOP) for distributed systems
 */
object NotificationQueueService {
    private val logger = Logger.getLogger(NotificationQueueService::class.java.name)
    
    // In-memory queue for notification messages
    private val queue = ConcurrentLinkedQueue<NotificationMessage>()
    
    // Channel for coroutine-based processing
    private val channel = Channel<NotificationMessage>(Channel.UNLIMITED)
    
    // Mutex for thread-safe operations
    private val mutex = Mutex()
    
    // Statistics
    private var totalEnqueued = 0L
    private var totalProcessed = 0L
    private var totalFailed = 0L
    @Volatile private var started = false
    
    /**
     * Enqueue a notification message
     */
    suspend fun enqueue(message: NotificationMessage) {
        mutex.withLock {
            queue.offer(message)
            channel.send(message)
            totalEnqueued++
            logger.info("✅ Notification message enqueued: ${message.messageId} (type=${message::class.simpleName}, Queue size: ${queue.size}, Total enqueued: $totalEnqueued)")
        }
    }
    
    /**
     * Enqueue challenge reward notification
     */
    suspend fun enqueueChallengeRewardNotification(
        userId: String,
        challengeTitle: String,
        rank: Int,
        coins: Long,
        challengeId: Long
    ) {
        val message = ChallengeRewardNotificationMessage(
            messageId = UUID.randomUUID().toString(),
            timestamp = Clock.System.now().toEpochMilliseconds(),
            userId = userId,
            challengeTitle = challengeTitle,
            rank = rank,
            coins = coins,
            challengeId = challengeId
        )
        enqueue(message)
    }
    
    /**
     * Enqueue challenge winner notification to other participants
     */
    suspend fun enqueueChallengeWinnerNotification(
        winnerUserId: String,
        winnerUsername: String,
        challengeTitle: String,
        coins: Long,
        challengeId: Long,
        otherUserIds: List<String>
    ) {
        val message = ChallengeWinnerNotificationMessage(
            messageId = UUID.randomUUID().toString(),
            timestamp = Clock.System.now().toEpochMilliseconds(),
            winnerUserId = winnerUserId,
            winnerUsername = winnerUsername,
            challengeTitle = challengeTitle,
            coins = coins,
            challengeId = challengeId,
            otherUserIds = otherUserIds
        )
        enqueue(message)
    }
    
    /**
     * Enqueue coins added notification
     */
    suspend fun enqueueCoinsAddedNotification(
        userId: String,
        amount: Long,
        source: String,
        description: String?
    ) {
        val message = CoinsAddedNotificationMessage(
            messageId = UUID.randomUUID().toString(),
            timestamp = Clock.System.now().toEpochMilliseconds(),
            userId = userId,
            amount = amount,
            source = source,
            description = description
        )
        enqueue(message)
    }
    
    /**
     * Enqueue reward catalog claimed notification
     */
    suspend fun enqueueRewardCatalogClaimedNotification(
        userId: String,
        rewardTitle: String,
        coinPrice: Long,
        transactionNumber: String,
        remainingCoins: Long
    ) {
        val message = RewardCatalogClaimedNotificationMessage(
            messageId = UUID.randomUUID().toString(),
            timestamp = Clock.System.now().toEpochMilliseconds(),
            userId = userId,
            rewardTitle = rewardTitle,
            coinPrice = coinPrice,
            transactionNumber = transactionNumber,
            remainingCoins = remainingCoins
        )
        enqueue(message)
    }
    
    /**
     * Enqueue transaction status update notification
     */
    suspend fun enqueueTransactionStatusNotification(
        userId: String,
        transactionNumber: String,
        rewardTitle: String,
        status: String,
        trackingNumber: String?
    ) {
        val message = TransactionStatusNotificationMessage(
            messageId = UUID.randomUUID().toString(),
            timestamp = Clock.System.now().toEpochMilliseconds(),
            userId = userId,
            transactionNumber = transactionNumber,
            rewardTitle = rewardTitle,
            status = status,
            trackingNumber = trackingNumber
        )
        enqueue(message)
    }
    
    /**
     * Enqueue low balance warning notification
     */
    suspend fun enqueueLowBalanceNotification(
        userId: String,
        currentBalance: Long
    ) {
        val message = LowBalanceNotificationMessage(
            messageId = UUID.randomUUID().toString(),
            timestamp = Clock.System.now().toEpochMilliseconds(),
            userId = userId,
            currentBalance = currentBalance
        )
        enqueue(message)
    }
    
    /**
     * Enqueue reward back in stock notification
     */
    suspend fun enqueueRewardBackInStockNotification(
        userIds: List<String>,
        rewardTitle: String,
        rewardCatalogId: Long,
        coinPrice: Long
    ) {
        val message = RewardBackInStockNotificationMessage(
            messageId = UUID.randomUUID().toString(),
            timestamp = Clock.System.now().toEpochMilliseconds(),
            userIds = userIds,
            rewardTitle = rewardTitle,
            rewardCatalogId = rewardCatalogId,
            coinPrice = coinPrice
        )
        enqueue(message)
    }
    
    /**
     * Get queue size
     */
    fun getQueueSize(): Int {
        return queue.size
    }
    
    /**
     * Get statistics
     */
    fun getStatistics(): QueueStatistics {
        return QueueStatistics(
            queueSize = queue.size,
            totalEnqueued = totalEnqueued,
            totalProcessed = totalProcessed,
            totalFailed = totalFailed
        )
    }
    
    /**
     * Get detailed statistics with breakdown by notification type
     */
    fun stats(): Map<String, Any> {
        val messageTypes = mutableMapOf<String, Int>()
        queue.forEach { message ->
            val typeName = message::class.simpleName ?: "Unknown"
            messageTypes[typeName] = messageTypes.getOrDefault(typeName, 0) + 1
        }
        
        return mapOf(
            "queueSize" to queue.size,
            "totalEnqueued" to totalEnqueued,
            "totalProcessed" to totalProcessed,
            "totalFailed" to totalFailed,
            "isStarted" to started,
            "messagesByType" to messageTypes
        )
    }
    
    /**
     * Start consuming messages from the queue.
     *
     * Important:
     * - This is safe to call multiple times; only the first call will start workers.
     * - Workers run on Dispatchers.IO because sending notifications may do network IO.
     */
    fun startConsumer(
        notificationService: NotificationService,
        scope: CoroutineScope,
        maxConcurrentWorkers: Int = 5
    ) {
        if (started) {
            logger.info("Notification queue consumer already started (workers=$maxConcurrentWorkers).")
            return
        }
        started = true

        logger.info("Starting notification queue consumer with $maxConcurrentWorkers workers...")

        repeat(maxConcurrentWorkers) { workerId ->
            scope.launch(Dispatchers.IO) {
                processMessages(notificationService, workerId)
            }
        }

        logger.info("Notification queue consumer started successfully")
    }
    
    // Note: we intentionally do not expose stop() yet, because worker lifecycles are owned by the
    // passed-in CoroutineScope. When that scope is cancelled (application shutdown), workers stop.
    
    /**
     * Process messages from the channel
     */
    private suspend fun processMessages(
        notificationService: NotificationService,
        workerId: Int
    ) {
        logger.info("Notification queue worker $workerId started")
        
        try {
            for (message in channel) {
                try {
                    when (message) {
                        is ChallengeRewardNotificationMessage -> {
                            logger.info("Worker $workerId processing challenge reward notification: ${message.messageId} for user ${message.userId}")
                            try {
                                notificationService.sendChallengeRewardNotification(
                                    userId = message.userId,
                                    challengeTitle = message.challengeTitle,
                                    rank = message.rank,
                                    coins = message.coins,
                                    challengeId = message.challengeId
                                )
                                logger.info("Worker $workerId successfully sent challenge reward notification to user ${message.userId}")
                            } catch (e: Exception) {
                                logger.severe("Worker $workerId failed to send challenge reward notification to user ${message.userId}: ${e.message}")
                                logger.severe("Exception: ${e.stackTraceToString()}")
                                throw e // Re-throw to be caught by outer catch block
                            }
                            mutex.withLock {
                                queue.remove(message)
                                totalProcessed++
                            }
                            logger.info("Worker $workerId successfully processed challenge reward notification: ${message.messageId}")
                        }
                        
                        is ChallengeWinnerNotificationMessage -> {
                            logger.info("Worker $workerId processing challenge winner notification: ${message.messageId} for ${message.otherUserIds.size} users")
                            try {
                                notificationService.sendChallengeWinnerNotificationToOthers(
                                    winnerUserId = message.winnerUserId,
                                    winnerUsername = message.winnerUsername,
                                    challengeTitle = message.challengeTitle,
                                    coins = message.coins,
                                    challengeId = message.challengeId,
                                    otherUserIds = message.otherUserIds
                                )
                                logger.info("Worker $workerId successfully sent challenge winner notifications to ${message.otherUserIds.size} users")
                            } catch (e: Exception) {
                                logger.severe("Worker $workerId failed to send challenge winner notifications: ${e.message}")
                                logger.severe("Exception: ${e.stackTraceToString()}")
                                throw e // Re-throw to be caught by outer catch block
                            }
                            mutex.withLock {
                                queue.remove(message)
                                totalProcessed++
                            }
                            logger.info("Worker $workerId successfully processed challenge winner notification: ${message.messageId}")
                        }
                        
                        is CoinsAddedNotificationMessage -> {
                            logger.info("Worker $workerId processing coins added notification: ${message.messageId} for user ${message.userId}")
                            try {
                                notificationService.sendCoinsAddedNotification(
                                    userId = message.userId,
                                    amount = message.amount,
                                    source = message.source,
                                    description = message.description
                                )
                                logger.info("Worker $workerId successfully sent coins added notification to user ${message.userId}")
                            } catch (e: Exception) {
                                logger.severe("Worker $workerId failed to send coins added notification: ${e.message}")
                                logger.severe("Exception: ${e.stackTraceToString()}")
                                throw e
                            }
                            mutex.withLock {
                                queue.remove(message)
                                totalProcessed++
                            }
                            logger.info("Worker $workerId successfully processed coins added notification: ${message.messageId}")
                        }
                        
                        is RewardCatalogClaimedNotificationMessage -> {
                            logger.info("Worker $workerId processing reward catalog claimed notification: ${message.messageId} for user ${message.userId}")
                            try {
                                notificationService.sendRewardCatalogClaimedNotification(
                                    userId = message.userId,
                                    rewardTitle = message.rewardTitle,
                                    coinPrice = message.coinPrice,
                                    transactionNumber = message.transactionNumber,
                                    remainingCoins = message.remainingCoins
                                )
                                logger.info("Worker $workerId successfully sent reward catalog claimed notification to user ${message.userId}")
                            } catch (e: Exception) {
                                logger.severe("Worker $workerId failed to send reward catalog claimed notification: ${e.message}")
                                logger.severe("Exception: ${e.stackTraceToString()}")
                                throw e
                            }
                            mutex.withLock {
                                queue.remove(message)
                                totalProcessed++
                            }
                            logger.info("Worker $workerId successfully processed reward catalog claimed notification: ${message.messageId}")
                        }
                        
                        is TransactionStatusNotificationMessage -> {
                            logger.info("Worker $workerId processing transaction status notification: ${message.messageId} for user ${message.userId}")
                            try {
                                notificationService.sendTransactionStatusNotification(
                                    userId = message.userId,
                                    transactionNumber = message.transactionNumber,
                                    rewardTitle = message.rewardTitle,
                                    status = message.status,
                                    trackingNumber = message.trackingNumber
                                )
                                logger.info("Worker $workerId successfully sent transaction status notification to user ${message.userId}")
                            } catch (e: Exception) {
                                logger.severe("Worker $workerId failed to send transaction status notification: ${e.message}")
                                logger.severe("Exception: ${e.stackTraceToString()}")
                                throw e
                            }
                            mutex.withLock {
                                queue.remove(message)
                                totalProcessed++
                            }
                            logger.info("Worker $workerId successfully processed transaction status notification: ${message.messageId}")
                        }
                        
                        is LowBalanceNotificationMessage -> {
                            logger.info("Worker $workerId processing low balance notification: ${message.messageId} for user ${message.userId}")
                            try {
                                notificationService.sendLowBalanceNotification(
                                    userId = message.userId,
                                    currentBalance = message.currentBalance
                                )
                                logger.info("Worker $workerId successfully sent low balance notification to user ${message.userId}")
                            } catch (e: Exception) {
                                logger.severe("Worker $workerId failed to send low balance notification: ${e.message}")
                                logger.severe("Exception: ${e.stackTraceToString()}")
                                throw e
                            }
                            mutex.withLock {
                                queue.remove(message)
                                totalProcessed++
                            }
                            logger.info("Worker $workerId successfully processed low balance notification: ${message.messageId}")
                        }
                        
                        is RewardBackInStockNotificationMessage -> {
                            logger.info("Worker $workerId processing reward back in stock notification: ${message.messageId} for ${message.userIds.size} users")
                            try {
                                for (userId in message.userIds) {
                                    notificationService.sendRewardBackInStockNotification(
                                        userId = userId,
                                        rewardTitle = message.rewardTitle,
                                        rewardCatalogId = message.rewardCatalogId,
                                        coinPrice = message.coinPrice
                                    )
                                }
                                logger.info("Worker $workerId successfully sent reward back in stock notifications to ${message.userIds.size} users")
                            } catch (e: Exception) {
                                logger.severe("Worker $workerId failed to send reward back in stock notifications: ${e.message}")
                                logger.severe("Exception: ${e.stackTraceToString()}")
                                throw e
                            }
                            mutex.withLock {
                                queue.remove(message)
                                totalProcessed++
                            }
                            logger.info("Worker $workerId successfully processed reward back in stock notification: ${message.messageId}")
                        }
                    }
                } catch (e: Exception) {
                    logger.severe("Worker $workerId failed to process notification ${message.messageId}: ${e.message}")
                    logger.severe("Exception details: ${e.stackTraceToString()}")
                    mutex.withLock {
                        totalFailed++
                        // Optionally, you could implement retry logic here
                        // For now, we just log and continue
                    }
                }
            }
        } catch (e: Exception) {
            logger.severe("Worker $workerId encountered fatal error: ${e.message}")
        }
    }
    
    /**
     * Clear the queue (for testing/debugging)
     */
    suspend fun clearQueue() {
        mutex.withLock {
            queue.clear()
            logger.info("Notification queue cleared")
        }
    }
}

/**
 * Queue statistics
 */
@Serializable
data class QueueStatistics(
    val queueSize: Int,
    val totalEnqueued: Long,
    val totalProcessed: Long,
    val totalFailed: Long
)

