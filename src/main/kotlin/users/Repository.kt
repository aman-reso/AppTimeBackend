package users

import com.apptime.code.common.dbTransaction
import com.apptime.code.users.Users
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import java.security.MessageDigest
import kotlinx.datetime.Instant

class UserRepository {

    /**
     * Generate unique username - appends number if username already exists
     * Note: This should be called within an existing transaction
     */
    private fun generateUniqueUsername(baseUsername: String): String {
        var username = baseUsername
        var counter = 1
        
        // Check if username exists, append number if it does
        while (Users.select { Users.username eq username }.count() > 0) {
            username = "${baseUsername}_$counter"
            counter++
        }
        
        return username
    }

    /**
     * Generate encrypted UserId from deviceId using SHA-256
     */
    private fun generateEncryptedUserId(deviceId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(deviceId.toByteArray())
        val hexString = hashBytes.joinToString("") { "%02x".format(it) }
        return "${hexString.substring(0, 8)}-${hexString.substring(8, 12)}-${
            hexString.substring(
                12,
                16
            )
        }-${hexString.substring(16, 20)}-${hexString.substring(20, 32)}"
    }

    /**
     * Register a new device/user
     */
    fun registerDevice(deviceInfo: DeviceInfo, firebaseToken: String? = null): DeviceRegistrationResponse {
        return dbTransaction {
            val userId = generateEncryptedUserId(deviceInfo.deviceId)

            // Check if user already exists (by userId or deviceId)
            val existingUser = Users.select {
                (Users.userId eq userId) or (Users.deviceId eq deviceInfo.deviceId)
            }.firstOrNull()

            if (existingUser != null) {
                // Check if user is blocked
                if (existingUser[Users.isBlocked]) {
                    throw IllegalArgumentException("User account is blocked")
                }
                
                // User already exists, return existing data
                val existingUserId = existingUser[Users.userId]
                val username = existingUser[Users.username]
                val createdAt = existingUser[Users.createdAt].toString()
                val totpEnabled = existingUser[Users.totpEnabled]
                val totpSecret = existingUser[Users.totpSecret]

                // Update device info if it changed
                Users.update({ Users.userId eq existingUserId }) {
                    it[deviceId] = deviceInfo.deviceId
                    it[manufacturer] = deviceInfo.manufacturer
                    it[model] = deviceInfo.model
                    it[brand] = deviceInfo.brand
                    it[product] = deviceInfo.product
                    it[device] = deviceInfo.device
                    it[hardware] = deviceInfo.hardware
                    it[androidVersion] = deviceInfo.androidVersion
                    it[sdkVersion] = deviceInfo.sdkVersion
                    // Update firebaseToken if provided
                    if (firebaseToken != null) {
                        it[Users.firebaseToken] = firebaseToken
                    }
                    it[updatedAt] = kotlinx.datetime.Clock.System.now()
                }

                return@dbTransaction DeviceRegistrationResponse(
                    userId = existingUserId,
                    username = username,
                    createdAt = createdAt,
                    totpSecret = totpSecret, // Return the secret for the user to use
                    totpEnabled = totpEnabled
                )
            }

            // Generate unique username from first 8 chars of userId
            val baseUsername = "user_${userId.replace("-", "").take(8)}"
            val username = generateUniqueUsername(baseUsername)
            val now = kotlinx.datetime.Clock.System.now()

            // Generate TOTP secret for new user (TOTP enabled by default)
            val totpSecret = TOTPUtil.generateSecret()

            // Insert new user
            Users.insert {
                it[Users.userId] = userId
                it[Users.username] = username
                it[deviceId] = deviceInfo.deviceId
                it[manufacturer] = deviceInfo.manufacturer
                it[model] = deviceInfo.model
                it[brand] = deviceInfo.brand
                it[product] = deviceInfo.product
                it[device] = deviceInfo.device
                it[hardware] = deviceInfo.hardware
                it[androidVersion] = deviceInfo.androidVersion
                it[sdkVersion] = deviceInfo.sdkVersion
                it[this.totpSecret] = totpSecret
                it[totpEnabled] = true
                it[Users.firebaseToken] = firebaseToken
                it[createdAt] = now
                it[updatedAt] = now
            }

            DeviceRegistrationResponse(
                userId = userId,
                username = username,
                createdAt = now.toString(),
                totpSecret = totpSecret, // Return the secret for the user to use
                totpEnabled = true
            )
        }
    }

    /**
     * Get user by userId
     */
    fun getUserById(userId: String): UserProfile? {
        return dbTransaction {
            Users.select { Users.userId eq userId }
                .firstOrNull()
                ?.let { row ->
                    UserProfile(
                        userId = row[Users.userId],
                        username = row[Users.username],
                        email = row[Users.email],
                        name = row[Users.name],
                        firebaseToken = row[Users.firebaseToken],
                        createdAt = row[Users.createdAt].toString(),
                        updatedAt = row[Users.updatedAt].toString(),
                        lastSyncTime = row[Users.lastSyncTime]?.toString() // ISO 8601 format
                    )
                }
        }
    }

    /**
     * Check if user exists
     */
    fun userExists(userId: String): Boolean {
        return dbTransaction {
            Users.select { Users.userId eq userId }.count() > 0
        }
    }
    
    /**
     * Check if user is blocked
     */
    fun isUserBlocked(userId: String): Boolean {
        return dbTransaction {
            Users.select { Users.userId eq userId }
                .firstOrNull()
                ?.let { row -> row[Users.isBlocked] } ?: false
        }
    }
    
    /**
     * Update username for a user
     */
    fun updateUsername(userId: String, newUsername: String): UserProfile? {
        return dbTransaction {
            // Check if new username is already taken by another user
            val existingUser = Users.select { Users.username eq newUsername }
                .firstOrNull()
            
            if (existingUser != null && existingUser[Users.userId] != userId) {
                throw IllegalArgumentException("Username already taken")
            }
            
            // Update username
            Users.update({ Users.userId eq userId }) {
                it[Users.username] = newUsername
                it[Users.updatedAt] = kotlinx.datetime.Clock.System.now()
            }
            
            // Return updated profile
            getUserById(userId)
        }
    }
    
    /**
     * Update user's Firebase token
     */
    fun updateFirebaseToken(userId: String, firebaseToken: String?): UserProfile? {
        return dbTransaction {
            Users.update({ Users.userId eq userId }) {
                it[Users.firebaseToken] = firebaseToken
                it[Users.updatedAt] = kotlinx.datetime.Clock.System.now()
            }
            getUserById(userId)
        }
    }
    
    /**
     * Update user's last sync time
     */
    fun updateLastSyncTime(userId: String, syncTime: kotlinx.datetime.Instant) {
        dbTransaction {
            Users.update({ Users.userId eq userId }) {
                it[Users.lastSyncTime] = syncTime
                it[Users.updatedAt] = kotlinx.datetime.Clock.System.now()
            }
        }
    }
    
    /**
     * Get user's last sync time
     */
    fun getLastSyncTime(userId: String): Long? {
        return dbTransaction {
            Users.select { Users.userId eq userId }
                .firstOrNull()
                ?.get(Users.lastSyncTime)
                ?.toEpochMilliseconds()
        }
    }
    
    /**
     * Get user's TOTP secret from database by username
     */
    fun getTOTPSecretByUsername(username: String): String? {
        return dbTransaction {
            Users.select { Users.username eq username }
                .firstOrNull()
                ?.get(Users.totpSecret)
        }
    }
    
    /**
     * Get user's TOTP secret from database by userId
     */
    fun getTOTPSecret(userId: String): String? {
        return dbTransaction {
            Users.select { Users.userId eq userId }
                .firstOrNull()
                ?.get(Users.totpSecret)
        }
    }
    
    /**
     * Check if user has TOTP enabled by username
     */
    fun isTOTPEnabledForUserByUsername(username: String): Boolean {
        return dbTransaction {
            Users.select { Users.username eq username }
                .firstOrNull()
                ?.let { row ->
                    val secret = row[Users.totpSecret]
                    val enabled = row[Users.totpEnabled]
                    enabled && !secret.isNullOrBlank()
                } ?: false
        }
    }
    
    /**
     * Check if user has TOTP enabled by userId
     */
    fun isTOTPEnabledForUser(userId: String): Boolean {
        return dbTransaction {
            Users.select { Users.userId eq userId }
                .firstOrNull()
                ?.let { row ->
                    val secret = row[Users.totpSecret]
                    val enabled = row[Users.totpEnabled]
                    enabled && !secret.isNullOrBlank()
                } ?: false
        }
    }
    
    /**
     * Get user profile by username (without userId)
     */
    fun getUserProfileByUsername(username: String): UserProfile? {
        return dbTransaction {
            Users.select { Users.username eq username }
                .firstOrNull()
                ?.let { row ->
                    UserProfile(
                        userId = row[Users.userId], // Keep userId for internal use, but won't expose in public API
                        username = row[Users.username],
                        email = row[Users.email],
                        name = row[Users.name],
                        firebaseToken = row[Users.firebaseToken],
                        createdAt = row[Users.createdAt].toString(),
                        updatedAt = row[Users.updatedAt].toString(),
                        lastSyncTime = row[Users.lastSyncTime]?.toString() // ISO 8601 format
                    )
                }
        }
    }
    
    /**
     * Search users by name (case-insensitive, partial match)
     * Returns up to 10 matching users
     */
    fun searchUsersByName(query: String, limit: Int = 10): List<UserSearchResult> {
        if (query.isBlank()) {
            return emptyList()
        }
        
        return dbTransaction {
            val searchPattern = "%${query.lowercase()}%"
            
            Users.select {
                ((Users.name.lowerCase() like searchPattern) or
                 (Users.username.lowerCase() like searchPattern)) and
                (Users.name.isNotNull() or Users.username.isNotNull())
            }
            .limit(limit)
            .map { row ->
                UserSearchResult(
                    username = row[Users.username], // Only username, never userId
                    email = row[Users.email],
                    name = row[Users.name],
                    createdAt = row[Users.createdAt].toString(),
                    isActive = row[Users.lastSyncTime] != null // Consider active if has sync time
                )
            }
        }
    }
    
    /**
     * Create a TOTP verification session
     * Invalidates any existing session for the same requestingUserId -> targetUserId pair
     * @param durationSeconds Duration in seconds for the session (default: 3600 = 1 hour)
     */
    fun createTOTPVerificationSession(
        requestingUserId: String,
        targetUserId: String,
        targetUsername: String?,
        durationSeconds: Int = 3600 // Default: 1 hour
    ): Long {
        return dbTransaction {
            val now = kotlinx.datetime.Clock.System.now()
            val expiresAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + (durationSeconds * 1000L))
            
            // Invalidate any existing active sessions for this pair
            TOTPVerificationSessions.update(
                where = {
                    (TOTPVerificationSessions.requestingUserId eq requestingUserId) and
                    (TOTPVerificationSessions.targetUserId eq targetUserId) and
                    (TOTPVerificationSessions.expiresAt greaterEq now)
                }
            ) {
                // Set expiresAt to now to invalidate old sessions
                it[TOTPVerificationSessions.expiresAt] = now
            }
            
            // Create new session
            TOTPVerificationSessions.insert {
                it[this.requestingUserId] = requestingUserId
                it[this.targetUserId] = targetUserId
                it[this.targetUsername] = targetUsername
                it[this.expiresAt] = expiresAt
            } get TOTPVerificationSessions.id
        }
    }
    
    /**
     * Check if a valid TOTP verification session exists
     * Returns true if requestingUserId has an active (non-expired) session for targetUserId
     */
    fun hasValidTOTPVerificationSession(
        requestingUserId: String,
        targetUserId: String
    ): Boolean {
        return dbTransaction {
            val now = kotlinx.datetime.Clock.System.now()
            val count = TOTPVerificationSessions.select {
                (TOTPVerificationSessions.requestingUserId eq requestingUserId) and
                (TOTPVerificationSessions.targetUserId eq targetUserId) and
                (TOTPVerificationSessions.expiresAt greaterEq now)
            }.count()
            
            count > 0
        }
    }
    
    /**
     * Get target user ID by username
     */
    fun getUserIdByUsername(username: String): String? {
        return dbTransaction {
            Users.select { Users.username eq username }
                .firstOrNull()
                ?.get(Users.userId)
        }
    }
    
    /**
     * Get TOTP verification session details with remaining time
     * Returns session info if valid, null otherwise
     */
    fun getTOTPVerificationSessionDetails(
        requestingUserId: String,
        targetUserId: String
    ): Map<String, Any>? {
        return dbTransaction {
            val now = kotlinx.datetime.Clock.System.now()
            val session = TOTPVerificationSessions.select {
                (TOTPVerificationSessions.requestingUserId eq requestingUserId) and
                (TOTPVerificationSessions.targetUserId eq targetUserId) and
                (TOTPVerificationSessions.expiresAt greaterEq now)
            }.orderBy(TOTPVerificationSessions.verifiedAt to SortOrder.DESC)
                .firstOrNull()
            
            session?.let {
                val expiresAt = it[TOTPVerificationSessions.expiresAt]
                val verifiedAt = it[TOTPVerificationSessions.verifiedAt]
                val remainingSeconds = (expiresAt.toEpochMilliseconds() - now.toEpochMilliseconds()) / 1000
                val remainingMinutes = remainingSeconds / 60
                
                mapOf(
                    "requestingUserId" to requestingUserId,
                    "targetUserId" to targetUserId,
                    "targetUsername" to (it[TOTPVerificationSessions.targetUsername] ?: ""),
                    "verifiedAt" to verifiedAt.toString(),
                    "expiresAt" to expiresAt.toString(),
                    "remainingSeconds" to remainingSeconds.toInt(),
                    "remainingMinutes" to remainingMinutes.toInt(),
                    "isValid" to true
                )
            }
        }
    }
    
    /**
     * Get all active sessions for a requesting user (for debugging)
     */
    fun getActiveSessionsForUser(requestingUserId: String): List<Map<String, Any>> {
        return dbTransaction {
            val now = kotlinx.datetime.Clock.System.now()
            TOTPVerificationSessions.select {
                (TOTPVerificationSessions.requestingUserId eq requestingUserId) and
                (TOTPVerificationSessions.expiresAt greaterEq now)
            }.orderBy(TOTPVerificationSessions.verifiedAt to SortOrder.DESC)
                .map { session ->
                    val expiresAt = session[TOTPVerificationSessions.expiresAt]
                    val remainingSeconds = (expiresAt.toEpochMilliseconds() - now.toEpochMilliseconds()) / 1000
                    val remainingMinutes = remainingSeconds / 60
                    
                    mapOf(
                        "targetUserId" to session[TOTPVerificationSessions.targetUserId],
                        "targetUsername" to (session[TOTPVerificationSessions.targetUsername] ?: ""),
                        "verifiedAt" to session[TOTPVerificationSessions.verifiedAt].toString(),
                        "expiresAt" to expiresAt.toString(),
                        "remainingSeconds" to remainingSeconds.toInt(),
                        "remainingMinutes" to remainingMinutes.toInt()
                    )
                }
        }
    }
}

