package com.apptime.code.appstats

import com.apptime.code.common.dbTransaction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import kotlinx.datetime.LocalDate

class AppStatsRepository {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Add app stats for a user and date (upsert - creates or updates)
     * If entry already exists, it will update the stats_json
     */
    fun addAppStats(
        userId: String,
        date: LocalDate,
        stats: List<AppStatEntry>
    ): AppStatsResponse {
        return dbTransaction {
            // Check if entry already exists
            val existing = AppStats.select {
                (AppStats.userId eq userId) and (AppStats.date eq date)
            }.firstOrNull()
            
            // Convert stats list to JSON string
            val statsJsonString = json.encodeToString(stats)
            
            if (existing != null) {
                // Update existing record
                AppStats.update({ (AppStats.userId eq userId) and (AppStats.date eq date) }) {
                    it[AppStats.statsJson] = statsJsonString
                    it[AppStats.updatedAt] = kotlinx.datetime.Clock.System.now()
                }
            } else {
                // Insert new record
                AppStats.insert {
                    it[AppStats.userId] = userId
                    it[AppStats.date] = date
                    it[AppStats.statsJson] = statsJsonString
                }
            }
            
            // Retrieve the updated/inserted record
            val record = AppStats.select {
                (AppStats.userId eq userId) and (AppStats.date eq date)
            }.first()
            
            // Parse stats JSON back to list
            val statsList = json.decodeFromString<List<AppStatEntry>>(record[AppStats.statsJson])
            
            AppStatsResponse(
                stats = statsList
            )
        }
    }
    
    /**
     * Update app stats for a user and date
     * Creates entry if it doesn't exist
     */
    fun updateAppStats(
        userId: String,
        date: LocalDate,
        stats: List<AppStatEntry>
    ): AppStatsResponse {
        return dbTransaction {
            // Check if entry exists
            val existing = AppStats.select {
                (AppStats.userId eq userId) and (AppStats.date eq date)
            }.firstOrNull()
            
            // Convert stats list to JSON string
            val statsJsonString = json.encodeToString(stats)
            
            if (existing != null) {
                // Update existing record
                AppStats.update({ (AppStats.userId eq userId) and (AppStats.date eq date) }) {
                    it[AppStats.statsJson] = statsJsonString
                    it[AppStats.updatedAt] = kotlinx.datetime.Clock.System.now()
                }
            } else {
                // Insert new record
                AppStats.insert {
                    it[AppStats.userId] = userId
                    it[AppStats.date] = date
                    it[AppStats.statsJson] = statsJsonString
                }
            }
            
            // Retrieve the updated/inserted record
            val record = AppStats.select {
                (AppStats.userId eq userId) and (AppStats.date eq date)
            }.first()
            
            // Parse stats JSON back to list
            val statsList = json.decodeFromString<List<AppStatEntry>>(record[AppStats.statsJson])
            
            AppStatsResponse(

                stats = statsList

            )
        }
    }
    
    /**
     * Get app stats for a user and date
     */
    fun getAppStats(
        userId: String,
        date: LocalDate
    ): AppStatsResponse? {
        return dbTransaction {
            val record = AppStats.select {
                (AppStats.userId eq userId) and (AppStats.date eq date)
            }.firstOrNull() ?: return@dbTransaction null
            
            // Parse stats JSON back to list
            val statsList = json.decodeFromString<List<AppStatEntry>>(record[AppStats.statsJson])
            
            AppStatsResponse(

                stats = statsList
            )
        }
    }
    
    /**
     * Get all app stats for a user
     */
    fun getAllAppStatsByUser(userId: String): List<AppStatsResponse> {
        return dbTransaction {
            AppStats.select { AppStats.userId eq userId }
                .orderBy(AppStats.date to SortOrder.DESC)
                .map { record ->
                    val statsList = json.decodeFromString<List<AppStatEntry>>(record[AppStats.statsJson])
                    AppStatsResponse(

                        stats = statsList
                    )
                }
        }
    }
    
    /**
     * Get app stats for a user within date range
     */
    fun getAppStatsByDateRange(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<AppStatsResponse> {
        return dbTransaction {
            AppStats.select {
                (AppStats.userId eq userId) and
                (AppStats.date greaterEq startDate) and
                (AppStats.date lessEq endDate)
            }
            .orderBy(AppStats.date to SortOrder.DESC)
            .map { record ->
                val statsList = json.decodeFromString<List<AppStatEntry>>(record[AppStats.statsJson])
                AppStatsResponse(

                    stats = statsList
                )
            }
        }
    }
}

