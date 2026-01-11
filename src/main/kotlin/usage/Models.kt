package usage

import kotlinx.serialization.Serializable

@Serializable
data class AppUsageData(
    val packageName: String,
    val appName: String? = null,
    val usageTime: Long, // milliseconds
    val lastUsed: Long? = null,
    val isSystemApp: Boolean = false,
    val category: String? = null
)

@Serializable
data class AppUsageSubmissionRequest(
    val appName: String,
    val packageName: String,
    val openedAt: String, // ISO 8601 format
    val duration: Long, // milliseconds
    val isSystemApp: Boolean,
    val totalScreenTime: Long // milliseconds
)

@Serializable
data class BatchUsageRecord(
    val packageName: String,
    val appName: String? = null,
    val usageTime: Long, // milliseconds
    val lastUsed: Long? = null,
    val isSystemApp: Boolean = false,
    val category: String? = null
)

@Serializable
data class HourlyUsageRequest(
    val date: String, // YYYY-MM-DD format
    val hourlyData: Map<String, List<AppUsageData>> // hour -> list of app usages
)

@Serializable
data class GetAppUsageRequest(
    val startDate: String,
    val endDate: String,
    val includeSystemApps: Boolean = false
)

@Serializable
data class GetAppUsageResponse(
    val totalScreenTime: Long,
    val appUsages: List<AppUsageData>,
    val timeFrame: String? = null
)

@Serializable
data class CompleteAppHistoryRequest(
    val page: Int = 1,
    val pageSize: Int = 50
)

@Serializable
data class CompleteAppHistoryResponse(
    val appUsages: List<AppUsageData>,
    val totalCount: Int,
    val page: Int,
    val pageSize: Int,
    val hasMore: Boolean
)

/**
 * Event types for app usage events
 */
enum class AppUsageEventType {
    MOVE_TO_FOREGROUND,
    MOVE_TO_BACKGROUND,
    APP_LAUNCHED,
    APP_CLOSED
}

/**
 * Request to submit an app usage event
 */
@Serializable
data class AppUsageEventRequest(
    val packageName: String,
    val appName: String? = null,
    val isSystemApp: Boolean = false,
    val event: String, // MOVE_TO_FOREGROUND, MOVE_TO_BACKGROUND, APP_LAUNCHED, APP_CLOSED
    val duration: Long, // Duration in milliseconds
    val startTime: Long, // Start time in milliseconds
    val endTime: Long // End time in milliseconds
)

/**
 * Request to submit a single app usage event with sync time
 */
@Serializable
data class AppUsageEventSubmissionRequest(
    val syncTime: String, // ISO 8601 format - top level sync time
    val event: AppUsageEventRequest
)

/**
 * Batch request to submit multiple app usage events with sync time
 */
@Serializable
data class BatchAppUsageEventRequest(
    val syncTime: String, // ISO 8601 format - top level sync time
    val events: List<AppUsageEventRequest>
)

/**
 * App usage event data model
 */
@Serializable
data class AppUsageEvent(
    val id: Long,
    val userId: String,
    val packageName: String,
    val appName: String? = null,
    val isSystemApp: Boolean = false,
    val eventType: String,
    val eventTimestamp: String, // ISO 8601 format
    val duration: Long? = null, // Optional duration in milliseconds
    val startTime: Long? = null, // Optional start time in milliseconds
    val endTime: Long? = null, // Optional end time in milliseconds
    val createdAt: String // ISO 8601 format
)

/**
 * Batch submission response
 */
@Serializable
data class BatchAppUsageEventResponse(
    val events: List<AppUsageEvent>,
    val count: Int
)

/**
 * Daily usage stats item - returns events exactly as submitted
 */
@Serializable
data class DailyUsageStatsItem(
    val packageName: String,
    val appName: String? = null,
    val isSystemApp: Boolean = false,
    val eventType: String, // MOVE_TO_FOREGROUND, MOVE_TO_BACKGROUND, APP_LAUNCHED, APP_CLOSED
    val eventTimestamp: String, // ISO 8601 format
    val duration: Long? = null // milliseconds - null if not provided (e.g., for MOVE_TO_FOREGROUND events)
)

/**
 * Daily usage stats response (encrypted)
 */
@Serializable
data class DailyUsageStatsResponse(
    val encryptedData: String, // Encrypted JSON string of List<DailyUsageStatsItem>
    val date: String // YYYY-MM-DD format
)

/**
 * Raw event data from database
 */
@Serializable
data class RawEventData(
    val id: String,
    val userId: String,
    val packageName: String,
    val appName: String? = null,
    val isSystemApp: Boolean = false,
    val eventType: String,
    val eventTimestamp: String,
    val duration: Long? = null, // Optional duration in milliseconds
    val startTime: Long? = null, // Optional start time in milliseconds
    val endTime: Long? = null, // Optional end time in milliseconds
    val createdAt: String
)

/**
 * Raw events response
 */
@Serializable
data class RawEventsResponse(
    val userId: String,
    val totalEvents: Int,
    val events: List<RawEventData>
)

/**
 * TOTP verification session info
 */
@Serializable
data class TOTPVerificationSessionInfo(
    val requestingUserId: String,
    val targetUserId: String,
    val targetUsername: String,
    val verifiedAt: String,
    val expiresAt: String,
    val remainingSeconds: Int,
    val remainingMinutes: Int,
    val isValid: Boolean
)

/**
 * Daily usage stats response with session info
 */
@Serializable
data class DailyUsageStatsWithSessionResponse(
    val stats: List<DailyUsageStatsItem>,
    val session: TOTPVerificationSessionInfo
)

/**
 * Response for last sync time of app usage stats
 */
@Serializable
data class AppUsageLastSyncResponse(
    val userId: String,
    val lastSyncTime: String?, // ISO 8601 format, null if no events submitted yet
    val hasEvents: Boolean // true if user has submitted any app usage events
)

/**
 * Response for deleting user events
 */
@Serializable
data class DeleteUserEventsResponse(
    val userId: String,
    val deletedCount: Int,
    val message: String
)

