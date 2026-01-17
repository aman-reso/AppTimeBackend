package com.apptime.code.appstats

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * App stats service layer - handles business logic
 */
class AppStatsService(
    private val repository: AppStatsRepository
) {
    
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    
    /**
     * Add app stats for a user and date
     */
    suspend fun addAppStats(
        userId: String,
        dateString: String,
        stats: List<AppStatEntry>
    ): AppStatsResponse {
        // Validate date format
        val date = parseDate(dateString)
        
        // Validate stats list is not empty
        if (stats.isEmpty()) {
            throw IllegalArgumentException("Stats list cannot be empty")
        }
        
        // Validate each stat entry
        stats.forEach { stat ->
            if (stat.appname.isBlank()) {
                throw IllegalArgumentException("App name cannot be blank")
            }
            if (stat.packagename.isBlank()) {
                throw IllegalArgumentException("Package name cannot be blank")
            }
            if (stat.duration <= 0) {
                throw IllegalArgumentException("Duration must be greater than 0")
            }
        }
        
        return repository.addAppStats(userId, date, stats)
    }
    
    /**
     * Update app stats for a user and date
     */
    suspend fun updateAppStats(
        userId: String,
        dateString: String,
        stats: List<AppStatEntry>
    ): AppStatsResponse {
        // Validate date format
        val date = parseDate(dateString)
        
        // Validate stats list is not empty
        if (stats.isEmpty()) {
            throw IllegalArgumentException("Stats list cannot be empty")
        }
        
        // Validate each stat entry
        stats.forEach { stat ->
            if (stat.appname.isBlank()) {
                throw IllegalArgumentException("App name cannot be blank")
            }
            if (stat.packagename.isBlank()) {
                throw IllegalArgumentException("Package name cannot be blank")
            }
            if (stat.duration <= 0) {
                throw IllegalArgumentException("Duration must be greater than 0")
            }
        }
        
        return repository.updateAppStats(userId, date, stats)
    }
    
    /**
     * Get app stats for a user and date
     */
    suspend fun getAppStats(
        userId: String,
        dateString: String
    ): AppStatsResponse? {
        val date = parseDate(dateString)
        return repository.getAppStats(userId, date)
    }
    
    /**
     * Get all app stats for a user
     */
    suspend fun getAllAppStatsByUser(userId: String): AppStatsListResponse {
        val stats = repository.getAllAppStatsByUser(userId)
        return AppStatsListResponse(
            stats = stats,
            total = stats.size
        )
    }
    
    /**
     * Get app stats for a user within date range
     */
    suspend fun getAppStatsByDateRange(
        userId: String,
        startDateString: String,
        endDateString: String
    ): AppStatsListResponse {
        val startDate = parseDate(startDateString)
        val endDate = parseDate(endDateString)
        
        if (endDate < startDate) {
            throw IllegalArgumentException("End date must be after or equal to start date")
        }
        
        val stats = repository.getAppStatsByDateRange(userId, startDate, endDate)
        return AppStatsListResponse(
            stats = stats,
            total = stats.size
        )
    }
    
    /**
     * Parse date string to LocalDate
     */
    private fun parseDate(dateString: String): LocalDate {
        return try {
            val javaDate = java.time.LocalDate.parse(dateString, dateFormatter)
            LocalDate(javaDate.year, javaDate.monthValue, javaDate.dayOfMonth)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("Invalid date format. Expected YYYY-MM-DD (e.g., 2024-01-15)", e)
        }
    }
}

