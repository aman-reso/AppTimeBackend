package usage

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * App usage events table - stores individual app lifecycle events
 * Tracks when apps move to foreground/background for detailed analytics
 */
object AppUsageEvents : Table("app_usage_events") {
    val id = long("id").autoIncrement()
    val userId = varchar("user_id", 255)
    val packageName = varchar("package_name", 255)
    val appName = varchar("app_name", 255).nullable()
    val isSystemApp = bool("is_system_app").default(false)
    val eventType = varchar("event_type", 100) // e.g. MOVE_TO_FOREGROUND, MOVE_TO_BACKGROUND, APP_LAUNCHED, APP_CLOSED
    val eventTimestamp = timestamp("event_timestamp")
    val duration = long("duration").nullable() // Optional duration in milliseconds
    val startTime = long("start_time").nullable() // Optional start time in milliseconds
    val endTime = long("end_time").nullable() // Optional end time in milliseconds
    val createdAt = timestamp("created_at").clientDefault { kotlinx.datetime.Clock.System.now() }
    
    override val primaryKey = PrimaryKey(id)
    
    init {
        // Single column indexes
        index(isUnique = false, userId)
        index(isUnique = false, packageName)
        index(isUnique = false, eventType)
        // Composite index for common queries: user events by time
        index(isUnique = false, userId, eventTimestamp)
        // Composite index for user + package + time (for app-specific history)
        index(isUnique = false, userId, packageName, eventTimestamp)
    }
}

