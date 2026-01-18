package com.apptime.code.notifications

import com.apptime.code.common.dbTransaction
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import kotlinx.datetime.Clock

/**
 * Repository for notification database operations
 */
class NotificationRepository {
    
    /**
     * Create a notification in the database
     */
    fun createNotification(
        userId: String,
        title: String,
        text: String,
        type: String? = null,
        image: String? = null,
        deeplink: String? = null
    ): Long {
        return dbTransaction {
            Notifications.insert {
                it[Notifications.userId] = userId
                it[Notifications.title] = title
                it[Notifications.text] = text
                it[Notifications.type] = type
                it[Notifications.image] = image
                it[Notifications.deeplink] = deeplink
                it[Notifications.isRead] = false
                it[Notifications.createdAt] = Clock.System.now()
            }[Notifications.id]
        }
    }
    
    /**
     * Get notifications for a user
     */
    fun getUserNotifications(
        userId: String,
        isRead: Boolean? = null,
        limit: Int? = null,
        offset: Int = 0
    ): List<NotificationData> {
        return dbTransaction {
            val query = Notifications.select {
                Notifications.userId eq userId
            }
            
            val filteredQuery = if (isRead != null) {
                query.andWhere { Notifications.isRead eq isRead }
            } else {
                query
            }
            
            val orderedQuery = filteredQuery.orderBy(Notifications.createdAt to SortOrder.DESC)
            
            val limitedQuery = if (limit != null) {
                orderedQuery.limit(limit, offset.toLong())
            } else {
                orderedQuery
            }
            
            limitedQuery.map { row ->
                NotificationData(
                    id = row[Notifications.id],
                    title = row[Notifications.title],
                    text = row[Notifications.text],
                    image = row[Notifications.image],
                    deeplink = row[Notifications.deeplink],
                    type = row[Notifications.type],
                    createdAt = row[Notifications.createdAt].toString(),
                    isRead = row[Notifications.isRead]
                )
            }
        }
    }
    
    /**
     * Get unread notification count for a user
     */
    fun getUnreadCount(userId: String): Int {
        return dbTransaction {
            Notifications.select {
                (Notifications.userId eq userId) and (Notifications.isRead eq false)
            }.count().toInt()
        }
    }
    
    /**
     * Mark notification as read
     */
    fun markAsRead(notificationId: Long, userId: String): Boolean {
        return dbTransaction {
            val updated = Notifications.update(
                where = {
                    (Notifications.id eq notificationId) and (Notifications.userId eq userId)
                }
            ) {
                it[Notifications.isRead] = true
                it[Notifications.readAt] = Clock.System.now()
            }
            updated > 0
        }
    }
    
    /**
     * Mark all notifications as read for a user
     */
    fun markAllAsRead(userId: String): Int {
        return dbTransaction {
            Notifications.update(
                where = {
                    (Notifications.userId eq userId) and (Notifications.isRead eq false)
                }
            ) {
                it[Notifications.isRead] = true
                it[Notifications.readAt] = Clock.System.now()
            }
        }
    }
    
    /**
     * Delete a notification
     */
    fun deleteNotification(notificationId: Long, userId: String): Boolean {
        return dbTransaction {
            val deleted = Notifications.deleteWhere {
                (Notifications.id eq notificationId) and (Notifications.userId eq userId)
            }
            deleted > 0
        }
    }
    
    /**
     * Get notification by ID
     */
    fun getNotificationById(notificationId: Long, userId: String): NotificationData? {
        return dbTransaction {
            Notifications.select {
                (Notifications.id eq notificationId) and (Notifications.userId eq userId)
            }.firstOrNull()?.let { row ->
                NotificationData(
                    id = row[Notifications.id],
                    title = row[Notifications.title],
                    text = row[Notifications.text],
                    image = row[Notifications.image],
                    deeplink = row[Notifications.deeplink],
                    type = row[Notifications.type],
                    createdAt = row[Notifications.createdAt].toString(),
                    isRead = row[Notifications.isRead]
                )
            }
        }
    }
}

