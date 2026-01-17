package com.apptime.code.appstats

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * App stats table - stores daily app usage statistics as JSON
 * Each row represents stats for a user on a specific date
 */
object AppStats : Table("app_stats") {
    val id = long("id").autoIncrement()
    val userId = varchar("user_id", 255).index()
    val date = date("date")
    val statsJson = text("stats_json") // JSON string containing list of app stats
    
    val createdAt = timestamp("created_at").clientDefault { kotlinx.datetime.Clock.System.now() }
    val updatedAt = timestamp("updated_at").clientDefault { kotlinx.datetime.Clock.System.now() }
    
    override val primaryKey = PrimaryKey(id)
    
    init {
        // Composite unique index to ensure one entry per user per date
        uniqueIndex(userId, date)
        // Index for querying by date (userId already has index from inline definition)
        index(isUnique = false, date)
    }
}

