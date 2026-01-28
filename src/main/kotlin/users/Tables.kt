package com.apptime.code.users

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * Users table - stores user accounts and device information
 */
object Users : Table("users") {
    val userId = varchar("user_id", 255).uniqueIndex()
    val username = varchar("username", 255).nullable().uniqueIndex()
    val email = varchar("email", 255).nullable()
    val name = varchar("name", 255).nullable()
    val country = varchar("country", 100).nullable() // User's country (ISO country code or name)

    // Device information
    val deviceId = varchar("device_id", 255).uniqueIndex()
    val manufacturer = varchar("manufacturer", 100).nullable()
    val model = varchar("model", 100).nullable()
    val brand = varchar("brand", 100).nullable()
    val product = varchar("product", 100).nullable()
    val device = varchar("device", 100).nullable()
    val hardware = varchar("hardware", 100).nullable()
    val androidVersion = varchar("android_version", 50).nullable()
    val sdkVersion = integer("sdk_version").nullable()

    // TOTP
    val totpSecret = varchar("totp_secret", 255).nullable()
    val totpEnabled = bool("totp_enabled").default(true)

    // User status
    val isBlocked = bool("is_blocked").default(false)

    // Firebase
    val firebaseToken = text("firebase_token").nullable() // Firebase Cloud Messaging (FCM) token for push notifications

    // Timestamps
    val createdAt = timestamp("created_at").clientDefault { kotlinx.datetime.Clock.System.now() }
    val updatedAt = timestamp("updated_at").clientDefault { kotlinx.datetime.Clock.System.now() }
    val lastSyncTime = timestamp("last_sync_time").nullable()

    override val primaryKey = PrimaryKey(userId)
}

