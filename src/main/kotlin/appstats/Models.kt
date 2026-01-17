package com.apptime.code.appstats

import kotlinx.serialization.Serializable

/**
 * Individual app stat entry within stats_json array
 */
@Serializable
data class AppStatEntry(
    val appname: String,
    val packagename: String,
    val duration: Long // Duration in milliseconds (e.g., 3600000 for 1 hour)
)

/**
 * Request to add/update app stats
 */
@Serializable
data class AddAppStatsRequest(
    val date: String, // Date in YYYY-MM-DD format
    val stats: List<AppStatEntry> // List of app stats
)

/**
 * Request to update app stats
 */
@Serializable
data class UpdateAppStatsRequest(
    val stats: List<AppStatEntry> // List of app stats to update
)

/**
 * Response model for app stats
 */
@Serializable
data class AppStatsResponse(
    val stats: List<AppStatEntry>,
)

/**
 * Response for fetching multiple stats
 */
@Serializable
data class AppStatsListResponse(
    val stats: List<AppStatsResponse>,
    val total: Int
)

