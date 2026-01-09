package com.apptime.code.features

import com.apptime.code.common.dbTransaction
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import kotlinx.datetime.Clock

class FeatureFlagsRepository {
    
    /**
     * Get all feature flags
     */
    fun getAllFeatureFlags(): List<FeatureFlag> {
        return dbTransaction {
            FeatureFlags.selectAll()
                .orderBy(FeatureFlags.featureName)
                .map { row ->
                    FeatureFlag(
                        id = row[FeatureFlags.id],
                        featureName = row[FeatureFlags.featureName],
                        isEnabled = row[FeatureFlags.isEnabled],
                        description = row[FeatureFlags.description],
                        conditions = ConditionEvaluator.parseConditions(row[FeatureFlags.conditions]),
                        createdAt = row[FeatureFlags.createdAt].toString(),
                        updatedAt = row[FeatureFlags.updatedAt].toString()
                    )
                }
        }
    }
    
    /**
     * Get feature flag by name
     */
    fun getFeatureFlagByName(featureName: String): FeatureFlag? {
        return dbTransaction {
            FeatureFlags.select { FeatureFlags.featureName eq featureName }
                .firstOrNull()
                ?.let { row ->
                    FeatureFlag(
                        id = row[FeatureFlags.id],
                        featureName = row[FeatureFlags.featureName],
                        isEnabled = row[FeatureFlags.isEnabled],
                        description = row[FeatureFlags.description],
                        conditions = ConditionEvaluator.parseConditions(row[FeatureFlags.conditions]),
                        createdAt = row[FeatureFlags.createdAt].toString(),
                        updatedAt = row[FeatureFlags.updatedAt].toString()
                    )
                }
        }
    }
    
    /**
     * Get feature flag by ID
     */
    fun getFeatureFlagById(id: Int): FeatureFlag? {
        return dbTransaction {
            FeatureFlags.select { FeatureFlags.id eq id }
                .firstOrNull()
                ?.let { row ->
                    FeatureFlag(
                        id = row[FeatureFlags.id],
                        featureName = row[FeatureFlags.featureName],
                        isEnabled = row[FeatureFlags.isEnabled],
                        description = row[FeatureFlags.description],
                        conditions = ConditionEvaluator.parseConditions(row[FeatureFlags.conditions]),
                        createdAt = row[FeatureFlags.createdAt].toString(),
                        updatedAt = row[FeatureFlags.updatedAt].toString()
                    )
                }
        }
    }
    
    /**
     * Create a new feature flag
     */
    fun createFeatureFlag(request: CreateFeatureFlagRequest): Int {
        return dbTransaction {
            FeatureFlags.insert {
                it[featureName] = request.featureName
                it[isEnabled] = request.isEnabled
                it[description] = request.description
                it[conditions] = ConditionEvaluator.serializeConditions(request.conditions)
                it[updatedAt] = Clock.System.now()
            }[FeatureFlags.id]
        }
    }
    
    /**
     * Update a feature flag
     */
    fun updateFeatureFlag(featureName: String, request: UpdateFeatureFlagRequest): Boolean {
        return dbTransaction {
            val updated = FeatureFlags.update({ FeatureFlags.featureName eq featureName }) {
                it[isEnabled] = request.isEnabled
                it[description] = request.description
                it[conditions] = ConditionEvaluator.serializeConditions(request.conditions)
                it[updatedAt] = Clock.System.now()
            }
            updated > 0
        }
    }
    
    /**
     * Update a feature flag by ID
     */
    fun updateFeatureFlagById(id: Int, request: UpdateFeatureFlagRequest): Boolean {
        return dbTransaction {
            val updated = FeatureFlags.update({ FeatureFlags.id eq id }) {
                it[isEnabled] = request.isEnabled
                it[description] = request.description
                it[conditions] = ConditionEvaluator.serializeConditions(request.conditions)
                it[updatedAt] = Clock.System.now()
            }
            updated > 0
        }
    }
    
    /**
     * Delete a feature flag
     */
    fun deleteFeatureFlag(featureName: String): Boolean {
        return dbTransaction {
            val deleted = FeatureFlags.deleteWhere { FeatureFlags.featureName eq featureName }
            deleted > 0
        }
    }
    
    /**
     * Check if feature flag exists
     */
    fun featureFlagExists(featureName: String): Boolean {
        return dbTransaction {
            FeatureFlags.select { FeatureFlags.featureName eq featureName }
                .count() > 0
        }
    }
}

