package com.apptime.code.notifications

import com.apptime.code.common.EnvLoader
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.ErrorCode
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import io.ktor.utils.io.core.*
import java.io.ByteArrayInputStream
import java.util.logging.Logger
import kotlin.text.toByteArray

/**
 * Service for sending push notifications via Firebase Cloud Messaging
 */
object FirebaseNotificationService {
    private val logger = Logger.getLogger(FirebaseNotificationService::class.java.name)
    private var initialized = false

    /**
     * Initialize Firebase Admin SDK
     * Supports two methods:
     * 1. FIREBASE_CREDENTIALS_JSON - JSON string containing the service account credentials
     * 2. FIREBASE_CREDENTIALS_PATH - Path to the service account JSON file
     * 
     * Priority: FIREBASE_CREDENTIALS_PATH > FIREBASE_CREDENTIALS_JSON
     */
    fun initialize() {
        if (initialized) {
            return
        }

        try {
            val credentialsPath = EnvLoader.getEnv("FIREBASE_CREDENTIALS_PATH")
            val credentialsJson = EnvLoader.getEnv("FIREBASE_CREDENTIALS_JSON")
            
            if (credentialsPath.isNullOrBlank() && credentialsJson.isNullOrBlank()) {
                logger.warning("⚠️  FIREBASE_CREDENTIALS_PATH or FIREBASE_CREDENTIALS_JSON not found. Push notifications will be disabled.")
                return
            }

            // Check if FirebaseApp already exists
            if (FirebaseApp.getApps().isEmpty()) {
                val credentials = if (!credentialsPath.isNullOrBlank()) {
                    // Load from file path
                    val file = java.io.File(credentialsPath)
                    if (!file.exists()) {
                        logger.severe("❌ Firebase credentials file not found at: $credentialsPath")
                        return
                    }
                    logger.info("📄 Loading Firebase credentials from file: $credentialsPath")
                    com.google.auth.oauth2.GoogleCredentials.fromStream(file.inputStream())
                } else {
                    // Load from JSON string
                    logger.info("📄 Loading Firebase credentials from JSON string")
                    com.google.auth.oauth2.GoogleCredentials.fromStream(
                        ByteArrayInputStream(credentialsJson?.toByteArray())
                    )
                }

                val options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build()

                FirebaseApp.initializeApp(options)
                logger.info("✅ Firebase Admin SDK initialized successfully")
            } else {
                logger.info("✅ Firebase Admin SDK already initialized")
            }

            initialized = true
        } catch (e: Exception) {
            logger.severe("❌ Failed to initialize Firebase Admin SDK: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Send a push notification to a single user
     * @param firebaseToken The FCM token of the user
     * @param title Notification title
     * @param body Notification body
     * @param data Additional data to send with the notification
     * @return true if notification was sent successfully, false otherwise
     */
    fun sendNotification(
        firebaseToken: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Boolean {
        if (!initialized) {
            initialize()
            if (!initialized) {
                logger.warning("⚠️  Firebase not initialized. Cannot send notification.")
                return false
            }
        }

        return try {
            val notification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build()

            val message = Message.builder()
                .setToken(firebaseToken)
                .setNotification(notification)
                .putAllData(data)
                .build()

            val response = FirebaseMessaging.getInstance().send(message)
            logger.info("✅ Notification sent successfully. Message ID: $response")
            true
        } catch (e: FirebaseMessagingException) {
            // Handle specific Firebase messaging errors
            when (e.errorCode) {
                ErrorCode.UNKNOWN, ErrorCode.INVALID_ARGUMENT -> {
                    // Token is invalid, expired, or app was uninstalled
                    logger.warning("⚠️  Invalid or expired FCM token. Token may need to be refreshed. Error: ${e.message}")
                    false
                }
                ErrorCode.DEADLINE_EXCEEDED -> {
                    logger.warning("⚠️  FCM quota exceeded. Please check your Firebase plan.")
                    false
                }
                ErrorCode.UNAVAILABLE -> {
                    logger.warning("⚠️  FCM service temporarily unavailable. Error: ${e.message}")
                    false
                }
                else -> {
                    logger.severe("❌ Failed to send notification: ${e.message} (Error Code: ${e.errorCode})")
                    e.printStackTrace()
                    false
                }
            }
        } catch (e: Exception) {
            logger.severe("❌ Failed to send notification: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Send push notifications to multiple users
     * @param firebaseTokens List of FCM tokens
     * @param title Notification title
     * @param body Notification body
     * @param data Additional data to send with the notification
     * @return Number of successfully sent notifications
     */
    fun sendBulkNotifications(
        firebaseTokens: List<String>,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Int {
        if (firebaseTokens.isEmpty()) {
            return 0
        }

        if (!initialized) {
            initialize()
            if (!initialized) {
                logger.warning("⚠️  Firebase not initialized. Cannot send notifications.")
                return 0
            }
        }

        var successCount = 0
        for (token in firebaseTokens) {
            if (token.isNotBlank()) {
                if (sendNotification(token, title, body, data)) {
                    successCount++
                }
            }
        }

        logger.info("📤 Sent $successCount out of ${firebaseTokens.size} notifications")
        return successCount
    }
}

