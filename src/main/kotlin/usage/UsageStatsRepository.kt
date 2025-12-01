package usage

import com.apptime.code.common.dbTransaction
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll

class UsageStatsRepository {

    /**
     * Get daily usage stats for a user for a specific date
     * Aggregates data from app_usage_events by package name
     */
    fun getDailyUsageStats(userId: String, date: String): List<DailyUsageStatsItem> {
        return dbTransaction {
            // Parse the date to get start and end of day
            val dateParts = date.trim().split("-")
            if (dateParts.size != 3) {
                return@dbTransaction emptyList()
            }

            val year = dateParts[0].toIntOrNull() ?: return@dbTransaction emptyList()
            val month = dateParts[1].toIntOrNull() ?: return@dbTransaction emptyList()
            val day = dateParts[2].toIntOrNull() ?: return@dbTransaction emptyList()

            // Create start and end of day timestamps in UTC
            // This ensures consistent date queries regardless of server timezone
            val startOfDay = java.time.LocalDate.of(year, month, day)
                .atStartOfDay(java.time.ZoneId.of("UTC"))
                .toInstant()
            val endOfDay = startOfDay.plusSeconds(86400) // Add 24 hours (start of next day)

            val startInstant = Instant.fromEpochMilliseconds(startOfDay.toEpochMilli())
            val endInstant = Instant.fromEpochMilliseconds(endOfDay.toEpochMilli())

            val endInstantExclusive = Instant.fromEpochMilliseconds(endOfDay.toEpochMilli() - 1000)

            val allEvents = AppUsageEvents.select {
                (AppUsageEvents.userId eq userId) and
                        (AppUsageEvents.eventTimestamp greaterEq startInstant) and
                        (AppUsageEvents.eventTimestamp lessEq endInstantExclusive)
            }.orderBy(AppUsageEvents.eventTimestamp to SortOrder.ASC).toList()

            if (allEvents.isEmpty()) {
                return@dbTransaction emptyList()
            }

            // Return events exactly as submitted (no aggregation, no calculation)
            allEvents.map { event ->
                DailyUsageStatsItem(
                    packageName = event[AppUsageEvents.packageName],
                    appName = event[AppUsageEvents.appName],
                    isSystemApp = event[AppUsageEvents.isSystemApp],
                    eventType = event[AppUsageEvents.eventType],
                    eventTimestamp = event[AppUsageEvents.eventTimestamp].toString(),
                    duration = event[AppUsageEvents.duration] // Use duration exactly as submitted (null if not provided)
                )
            }
        }
    }

    /**
     * Get debug information about available data for a user
     */
    fun getDebugInfo(userId: String, date: String?): Map<String, Any> {
        return dbTransaction {
            try {
                // Get all events for the user
                val allEvents = AppUsageEvents.select {
                    AppUsageEvents.userId eq userId
                }.orderBy(AppUsageEvents.eventTimestamp to SortOrder.DESC).toList()

                // Extract unique dates from event timestamps
                val uniqueDates = allEvents.map { event ->
                    val timestamp = event[AppUsageEvents.eventTimestamp]
                    val localDate = java.time.Instant.ofEpochMilli(timestamp.toEpochMilliseconds())
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                    localDate.toString()
                }.distinct().sorted()

                // Filter by date if provided
                val filteredEvents = if (date != null) {
                    val trimmedDate = date.trim()
                    allEvents.filter { event ->
                        val timestamp = event[AppUsageEvents.eventTimestamp]
                        val localDate = java.time.Instant.ofEpochMilli(timestamp.toEpochMilliseconds())
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                        localDate.toString() == trimmedDate
                    }
                } else {
                    allEvents
                }

                // Get sample records
                val sampleRecords = filteredEvents.take(5).map { event ->
                    mapOf(
                        "id" to event[AppUsageEvents.id].toString(),
                        "packageName" to (event[AppUsageEvents.packageName] ?: ""),
                        "appName" to (event[AppUsageEvents.appName] ?: ""),
                        "eventType" to event[AppUsageEvents.eventType],
                        "eventTimestamp" to event[AppUsageEvents.eventTimestamp].toString()
                    )
                }

                // Get date counts
                val dateCounts = allEvents.map { event ->
                    val timestamp = event[AppUsageEvents.eventTimestamp]
                    val localDate = java.time.Instant.ofEpochMilli(timestamp.toEpochMilliseconds())
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                    localDate.toString()
                }.groupBy { it }.mapValues { it.value.size }

                mapOf(
                    "userId" to userId,
                    "requestedDate" to (date ?: "all dates"),
                    "totalEvents" to filteredEvents.size,
                    "uniqueDates" to uniqueDates,
                    "sampleRecords" to sampleRecords,
                    "dateCounts" to dateCounts
                )
            } catch (e: Exception) {
                mapOf(
                    "error" to (e.message ?: "Unknown error"),
                    "errorType" to e.javaClass.simpleName,
                    "userId" to userId,
                    "requestedDate" to (date ?: "all dates")
                )
            }
        }
    }

    /**
     * Get all raw events for a user from the database
     */
    fun getAllRawEventsForUser(userId: String): List<RawEventData> {
        return dbTransaction {
            try {
                val allEvents = AppUsageEvents.select {
                    AppUsageEvents.userId eq userId
                }.orderBy(AppUsageEvents.eventTimestamp to SortOrder.DESC).toList()
                
                    allEvents.map { event ->
                        RawEventData(
                            id = event[AppUsageEvents.id].toString(),
                            userId = event[AppUsageEvents.userId],
                            packageName = event[AppUsageEvents.packageName],
                            appName = event[AppUsageEvents.appName],
                            isSystemApp = event[AppUsageEvents.isSystemApp],
                            eventType = event[AppUsageEvents.eventType],
                            eventTimestamp = event[AppUsageEvents.eventTimestamp].toString(),
                            duration = event[AppUsageEvents.duration],
                            createdAt = event[AppUsageEvents.createdAt].toString()
                        )
                    }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * Get all userIds that have data in the app_usage_events table
     */
    fun getAllUserIdsWithData(): Map<String, Any> {
        return dbTransaction {
            try {
                // Get all unique userIds from events
                val allEvents = AppUsageEvents.selectAll().toList()
                val uniqueUserIds = allEvents.map { it[AppUsageEvents.userId] }.distinct()

                // Get event count per userId
                val userIdCounts = allEvents.groupBy { it[AppUsageEvents.userId] }.mapValues { it.value.size }

                // Get sample data for each userId
                val userIdSamples = uniqueUserIds.map { uid ->
                    val userEvents = allEvents.filter { it[AppUsageEvents.userId] == uid }
                    val sampleEvent = userEvents.firstOrNull()

                    // Extract unique dates from event timestamps
                    val uniqueDates = userEvents.map { event ->
                        val timestamp = event[AppUsageEvents.eventTimestamp]
                        val localDate = java.time.Instant.ofEpochMilli(timestamp.toEpochMilliseconds())
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                        localDate.toString()
                    }.distinct().sorted()

                    mapOf(
                        "userId" to uid,
                        "totalEvents" to userEvents.size,
                        "uniqueDates" to uniqueDates.take(10), // First 10 dates
                        "sampleEvent" to (sampleEvent?.let { event ->
                            mapOf(
                                "packageName" to (event[AppUsageEvents.packageName] ?: ""),
                                "appName" to (event[AppUsageEvents.appName] ?: ""),
                                "eventType" to event[AppUsageEvents.eventType],
                                "eventTimestamp" to event[AppUsageEvents.eventTimestamp].toString()
                            )
                        } ?: emptyMap<String, Any>())
                    )
                }

                mapOf(
                    "totalUserIds" to uniqueUserIds.size,
                    "allUserIds" to uniqueUserIds,
                    "userIdCounts" to userIdCounts,
                    "userIdSamples" to userIdSamples
                )
            } catch (e: Exception) {
                mapOf(
                    "error" to (e.message ?: "Unknown error"),
                    "errorType" to e.javaClass.simpleName,
                    "stackTrace" to e.stackTraceToString()
                )
            }
        }
    }
}

