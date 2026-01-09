package com.apptime.code.features

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString

/**
 * Evaluates feature flag conditions
 */
object ConditionEvaluator {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Parse conditions from JSON string
     */
    fun parseConditions(conditionsJson: String?): FeatureConditions? {
        if (conditionsJson.isNullOrBlank()) {
            return null
        }
        
        return try {
            val jsonObject = json.parseToJsonElement(conditionsJson).jsonObject
            FeatureConditions(
                countries = jsonObject["countries"]?.jsonArray?.map { it.jsonPrimitive.content },
                appVersions = jsonObject["appVersions"]?.jsonArray?.map { it.jsonPrimitive.content },
                languages = jsonObject["languages"]?.jsonArray?.map { it.jsonPrimitive.content },
                userIds = jsonObject["userIds"]?.jsonArray?.map { it.jsonPrimitive.content },
                percentage = jsonObject["percentage"]?.jsonPrimitive?.content?.toIntOrNull()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Serialize conditions to JSON string
     */
    fun serializeConditions(conditions: FeatureConditions?): String? {
        if (conditions == null) {
            return null
        }
        
        return try {
            val map = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
            conditions.countries?.let { 
                map["countries"] = kotlinx.serialization.json.JsonArray(
                    it.map { country -> kotlinx.serialization.json.JsonPrimitive(country) }
                )
            }
            conditions.appVersions?.let { 
                map["appVersions"] = kotlinx.serialization.json.JsonArray(
                    it.map { version -> kotlinx.serialization.json.JsonPrimitive(version) }
                )
            }
            conditions.languages?.let { 
                map["languages"] = kotlinx.serialization.json.JsonArray(
                    it.map { lang -> kotlinx.serialization.json.JsonPrimitive(lang) }
                )
            }
            conditions.userIds?.let { 
                map["userIds"] = kotlinx.serialization.json.JsonArray(
                    it.map { userId -> kotlinx.serialization.json.JsonPrimitive(userId) }
                )
            }
            conditions.percentage?.let { 
                map["percentage"] = kotlinx.serialization.json.JsonPrimitive(it)
            }
            JsonObject(map).toString()
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Check if a feature flag should be enabled based on conditions and evaluation parameters
     */
    fun evaluateFeature(
        isEnabled: Boolean,
        conditions: FeatureConditions?,
        params: FeatureEvaluationParams
    ): Boolean {
        // If feature is disabled globally, return false
        if (!isEnabled) {
            return false
        }
        
        // If no conditions, feature is enabled for all
        if (conditions == null) {
            return true
        }
        
        // Check country condition
        if (conditions.countries != null && conditions.countries.isNotEmpty()) {
            if (params.country == null || !conditions.countries.contains(params.country.uppercase())) {
                return false
            }
        }
        
        // Check language condition
        if (conditions.languages != null && conditions.languages.isNotEmpty()) {
            if (params.language == null || !conditions.languages.contains(params.language.lowercase())) {
                return false
            }
        }
        
        // Check app version condition
        if (conditions.appVersions != null && conditions.appVersions.isNotEmpty()) {
            if (params.appVersion == null || !matchesVersionConstraints(params.appVersion, conditions.appVersions)) {
                return false
            }
        }
        
        // Check user ID condition (for beta testing)
        if (conditions.userIds != null && conditions.userIds.isNotEmpty()) {
            if (params.userId == null || !conditions.userIds.contains(params.userId)) {
                return false
            }
        }
        
        // Check percentage rollout
        if (conditions.percentage != null) {
            if (params.userId == null || !isInPercentageRollout(params.userId, conditions.percentage)) {
                return false
            }
        }
        
        // All conditions passed
        return true
    }
    
    /**
     * Check if version matches any of the constraints
     */
    private fun matchesVersionConstraints(version: String, constraints: List<String>): Boolean {
        return constraints.any { constraint ->
            when {
                constraint.startsWith(">=") -> {
                    val minVersion = constraint.removePrefix(">=")
                    compareVersions(version, minVersion) >= 0
                }
                constraint.startsWith("<=") -> {
                    val maxVersion = constraint.removePrefix("<=")
                    compareVersions(version, maxVersion) <= 0
                }
                constraint.startsWith(">") -> {
                    val minVersion = constraint.removePrefix(">")
                    compareVersions(version, minVersion) > 0
                }
                constraint.startsWith("<") -> {
                    val maxVersion = constraint.removePrefix("<")
                    compareVersions(version, maxVersion) < 0
                }
                constraint.startsWith("=") -> {
                    val exactVersion = constraint.removePrefix("=")
                    compareVersions(version, exactVersion) == 0
                }
                else -> version == constraint
            }
        }
    }
    
    /**
     * Compare two version strings (simple semantic versioning)
     * Returns: negative if v1 < v2, zero if v1 == v2, positive if v1 > v2
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLength = maxOf(parts1.size, parts2.size)
        
        for (i in 0 until maxLength) {
            val part1 = parts1.getOrNull(i) ?: 0
            val part2 = parts2.getOrNull(i) ?: 0
            when {
                part1 < part2 -> return -1
                part1 > part2 -> return 1
            }
        }
        return 0
    }
    
    /**
     * Check if user is in percentage rollout (deterministic based on userId hash)
     */
    private fun isInPercentageRollout(userId: String, percentage: Int): Boolean {
        if (percentage <= 0) return false
        if (percentage >= 100) return true
        
        // Create a deterministic hash from userId
        val hash = userId.hashCode()
        val normalized = Math.abs(hash) % 100
        return normalized < percentage
    }
}

