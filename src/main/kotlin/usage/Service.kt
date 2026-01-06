package usage

import users.UserRepository
import kotlinx.datetime.Instant

class AppUsageEventService(
    private val repository: AppUsageEventRepository,
    private val userRepository: UserRepository
) {
    
    /**
     * Submit a single app usage event with sync time
     */
    suspend fun submitEvent(
        userId: String,
        request: AppUsageEventSubmissionRequest
    ): AppUsageEvent {
        // Update user's last sync time
        val syncTime = try {
            Instant.parse(request.syncTime)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid sync time format. Use ISO 8601 format.")
        }
        userRepository.updateLastSyncTime(userId, syncTime)
        
        // Submit the event
        return submitEventInternal(userId, request.event, request.event.duration)
    }
    
    /**
     * Submit a single app usage event (internal method)
     */
    private suspend fun submitEventInternal(
        userId: String,
        request: AppUsageEventRequest,
        duration: Long? = null
    ): AppUsageEvent {
        if (request.packageName.isBlank()) {
            throw IllegalArgumentException("Package name is required")
        }
        
        if (request.eventType.isBlank()) {
            throw IllegalArgumentException("Event type is required")
        }
        
        // Validate event type
        val validEventTypes = listOf(
            "MOVE_TO_FOREGROUND",
            "MOVE_TO_BACKGROUND",
            "APP_LAUNCHED",
            "APP_CLOSED"
        )
        
        if (request.eventType !in validEventTypes) {
            throw IllegalArgumentException(
                "Invalid event type. Must be one of: ${validEventTypes.joinToString(", ")}"
            )
        }
        
        // Parse timestamp
        val eventTimestamp = try {
            Instant.parse(request.eventTimestamp)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid event timestamp format. Use ISO 8601 format.")
        }
        
        return repository.submitEvent(
            userId = userId,
            packageName = request.packageName,
            appName = request.appName,
            isSystemApp = request.isSystemApp,
            eventType = request.eventType,
            eventTimestamp = eventTimestamp,
            duration = duration ?: request.duration,
            startTime = request.startTime,
            endTime = request.endTime
        )
    }
    
    /**
     * Submit multiple app usage events in batch with sync time
     */
    suspend fun submitBatchEvents(
        userId: String,
        request: BatchAppUsageEventRequest
    ): List<AppUsageEvent> {
        // Update user's last sync time
        val syncTime = try {
            Instant.parse(request.syncTime)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid sync time format. Use ISO 8601 format.")
        }
        userRepository.updateLastSyncTime(userId, syncTime)
        
        // Submit the events
        if (request.events.isEmpty()) {
            throw IllegalArgumentException("Events list cannot be empty")
        }
        
        
        // Validate all events
        request.events.forEach { event ->
            if (event.packageName.isBlank()) {
                throw IllegalArgumentException("Package name is required for all events")
            }
            
            if (event.eventType.isBlank()) {
                throw IllegalArgumentException("Event type is required for all events")
            }
            
            // Validate event type
            val validEventTypes = listOf(
                "MOVE_TO_FOREGROUND",
                "MOVE_TO_BACKGROUND",
                "APP_LAUNCHED",
                "APP_CLOSED"
            )
            
            if (event.eventType !in validEventTypes) {
                throw IllegalArgumentException(
                    "Invalid event type: ${event.eventType}. Must be one of: ${validEventTypes.joinToString(", ")}"
                )
            }
            
            // Validate timestamp
            try {
                Instant.parse(event.eventTimestamp)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid event timestamp format for event: ${event.packageName}. Use ISO 8601 format.")
            }
        }
        
        return repository.submitBatchEvents(userId, request.events)
    }
}

/**
 * Service for usage stats operations
 */
class UsageStatsService(
    private val repository: UsageStatsRepository
) {
    
    /**
     * Get daily usage stats for a user (unencrypted for now)
     */
    suspend fun getDailyUsageStats(userId: String, date: String): List<DailyUsageStatsItem> {
        // Validate date format (YYYY-MM-DD)
        if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            throw IllegalArgumentException("Invalid date format. Use YYYY-MM-DD format.")
        }
        // Get usage stats from repository
        return repository.getDailyUsageStats(userId, date)
    }
}

/**
 * Service for app usage event operations
 */
class AppUsageEventStatsService(
    private val repository: AppUsageEventRepository
) {
    /**
     * Get the last sync time for app usage stats
     */
    suspend fun getLastSyncTime(userId: String): AppUsageLastSyncResponse {
        val lastSyncTime = repository.getLastSyncTime(userId)
        return AppUsageLastSyncResponse(
            userId = userId,
            lastSyncTime = lastSyncTime?.toString(),
            hasEvents = lastSyncTime != null
        )
    }
    
    /**
     * Delete all app usage events for a user
     */
    suspend fun deleteUserEvents(userId: String): DeleteUserEventsResponse {
        val deletedCount = repository.deleteUserEvents(userId)
        return DeleteUserEventsResponse(
            userId = userId,
            deletedCount = deletedCount,
            message = "Successfully deleted $deletedCount event(s) for user"
        )
    }
}

