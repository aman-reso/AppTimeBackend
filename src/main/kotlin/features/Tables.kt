package com.apptime.code.features

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Feature flags table - stores feature configuration
 * Allows backend to control which features are enabled/disabled
 * Supports conditional flags based on country, app version, language, etc.
 */
object FeatureFlags : Table("feature_flags") {
    val id = integer("id").autoIncrement()
    val featureName = varchar("feature_name", 100).uniqueIndex()
    val isEnabled = bool("is_enabled").default(false)
    val description = text("description").nullable()
    // Conditions stored as JSON: {"countries": ["US", "IN"], "appVersions": [">=1.0.0"], "languages": ["en"]}
    // If conditions is null, feature applies to all
    val conditions = text("conditions").nullable()
    val createdAt = timestamp("created_at").clientDefault { kotlinx.datetime.Clock.System.now() }
    val updatedAt = timestamp("updated_at").clientDefault { kotlinx.datetime.Clock.System.now() }
    
    override val primaryKey = PrimaryKey(id)
}

