package com.apptime.code.admin

import com.apptime.code.challenges.ChallengeParticipants
import com.apptime.code.challenges.Challenges
import com.apptime.code.common.dbTransaction
import com.apptime.code.consents.ConsentTemplates
import com.apptime.code.consents.UserConsents
import com.apptime.code.users.Users
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class AdminRepository {
    
    // Challenge CRUD
    fun getAllChallenges(): List<AdminChallengeResponse> {
        return dbTransaction {
            Challenges.selectAll()
                .orderBy(Challenges.createdAt to SortOrder.DESC)
                .map { row ->
                    val challengeId = row[Challenges.id]
                    val participantCount = ChallengeParticipants.select {
                        ChallengeParticipants.challengeId eq challengeId
                    }.count().toLong()
                    
                    AdminChallengeResponse(
                        id = challengeId,
                        title = row[Challenges.title],
                        description = row[Challenges.description],
                        reward = row[Challenges.reward],
                        prize = row[Challenges.prize],
                        rules = row[Challenges.rules],
                        startTime = row[Challenges.startTime].toString(),
                        endTime = row[Challenges.endTime].toString(),
                        thumbnail = row[Challenges.thumbnail],
                        challengeType = row[Challenges.challengeType],
                        packageNames = row[Challenges.packageNames],
                        displayType = row[Challenges.displayType],
                        tags = row[Challenges.tags],
                        sponsor = row[Challenges.sponsor],
                        isActive = row[Challenges.isActive],
                        participantCount = participantCount,
                        createdAt = row[Challenges.createdAt].toString()
                    )
                }
        }
    }
    
    fun getChallengeById(id: Long): AdminChallengeResponse? {
        return dbTransaction {
            Challenges.select { Challenges.id eq id }
                .firstOrNull()
                ?.let { row ->
                    val participantCount = ChallengeParticipants.select {
                        ChallengeParticipants.challengeId eq id
                    }.count().toLong()
                    
                    AdminChallengeResponse(
                        id = row[Challenges.id],
                        title = row[Challenges.title],
                        description = row[Challenges.description],
                        reward = row[Challenges.reward],
                        prize = row[Challenges.prize],
                        rules = row[Challenges.rules],
                        startTime = row[Challenges.startTime].toString(),
                        endTime = row[Challenges.endTime].toString(),
                        thumbnail = row[Challenges.thumbnail],
                        challengeType = row[Challenges.challengeType],
                        packageNames = row[Challenges.packageNames],
                        displayType = row[Challenges.displayType],
                        tags = row[Challenges.tags],
                        sponsor = row[Challenges.sponsor],
                        isActive = row[Challenges.isActive],
                        participantCount = participantCount,
                        createdAt = row[Challenges.createdAt].toString()
                    )
                }
        }
    }
    
    fun createChallenge(request: CreateChallengeRequest): Long {
        return dbTransaction {
            Challenges.insert {
                it[title] = request.title
                it[description] = request.description
                it[reward] = request.reward
                it[prize] = request.prize
                it[rules] = request.rules
                it[startTime] = Instant.parse(request.startTime)
                it[endTime] = Instant.parse(request.endTime)
                it[thumbnail] = request.thumbnail
                it[challengeType] = request.challengeType
                it[packageNames] = request.packageNames
                it[displayType] = request.displayType
                it[tags] = request.tags
                it[sponsor] = request.sponsor
                it[isActive] = request.isActive
            } get Challenges.id
        }
    }
    
    fun updateChallenge(id: Long, request: UpdateChallengeRequest): Boolean {
        return dbTransaction {
            val updateCount = Challenges.update({ Challenges.id eq id }) {
                request.title?.let { value -> it[Challenges.title] = value }
                request.description?.let { value -> it[Challenges.description] = value }
                request.reward?.let { value -> it[Challenges.reward] = value }
                request.prize?.let { value -> it[Challenges.prize] = value }
                request.rules?.let { value -> it[Challenges.rules] = value }
                request.startTime?.let { value -> it[Challenges.startTime] = Instant.parse(value) }
                request.endTime?.let { value -> it[Challenges.endTime] = Instant.parse(value) }
                request.thumbnail?.let { value -> it[Challenges.thumbnail] = value }
                request.challengeType?.let { value -> it[Challenges.challengeType] = value }
                request.packageNames?.let { value -> it[Challenges.packageNames] = value }
                request.displayType?.let { value -> it[Challenges.displayType] = value }
                request.tags?.let { value -> it[Challenges.tags] = value }
                request.sponsor?.let { value -> it[Challenges.sponsor] = value }
                request.isActive?.let { value -> it[Challenges.isActive] = value }
            }
            updateCount > 0
        }
    }
    
    fun deleteChallenge(id: Long): Boolean {
        return dbTransaction {
            val deleteCount = Challenges.deleteWhere { Challenges.id eq id }
            deleteCount > 0
        }
    }
    
    // User Management
    fun getAllUsers(limit: Int = 100, offset: Int = 0): List<AdminUserResponse> {
        return dbTransaction {
            Users.selectAll()
                .orderBy(Users.createdAt to SortOrder.DESC)
                .limit(limit, offset.toLong())
                .map { row ->
                    AdminUserResponse(
                        userId = row[Users.userId],
                        username = row[Users.username],
                        email = row[Users.email],
                        name = row[Users.name],
                        deviceId = row[Users.deviceId],
                        deviceModel = row[Users.model],
                        manufacturer = row[Users.manufacturer],
                        androidVersion = row[Users.androidVersion],
                        totpEnabled = row[Users.totpEnabled],
                        createdAt = row[Users.createdAt].toString(),
                        lastSyncTime = row[Users.lastSyncTime]?.toString()
                    )
                }
        }
    }
    
    fun getUserById(userId: String): AdminUserResponse? {
        return dbTransaction {
            Users.select { Users.userId eq userId }
                .firstOrNull()
                ?.let { row ->
                    AdminUserResponse(
                        userId = row[Users.userId],
                        username = row[Users.username],
                        email = row[Users.email],
                        name = row[Users.name],
                        deviceId = row[Users.deviceId],
                        deviceModel = row[Users.model],
                        manufacturer = row[Users.manufacturer],
                        androidVersion = row[Users.androidVersion],
                        totpEnabled = row[Users.totpEnabled],
                        createdAt = row[Users.createdAt].toString(),
                        lastSyncTime = row[Users.lastSyncTime]?.toString()
                    )
                }
        }
    }
    
    fun updateUser(userId: String, request: UpdateUserRequest): Boolean {
        return dbTransaction {
            val updateCount = Users.update({ Users.userId eq userId }) {
                request.username?.let { value -> it[Users.username] = value }
                request.email?.let { value -> it[Users.email] = value }
                request.name?.let { value -> it[Users.name] = value }
                request.totpEnabled?.let { value -> it[Users.totpEnabled] = value }
            }
            updateCount > 0
        }
    }
    
    fun deleteUser(userId: String): Boolean {
        return dbTransaction {
            val deleteCount = Users.deleteWhere { Users.userId eq userId }
            deleteCount > 0
        }
    }
    
    // Consent Template Management
    fun getAllConsentTemplates(): List<AdminConsentTemplateResponse> {
        return dbTransaction {
            ConsentTemplates.selectAll()
                .orderBy(ConsentTemplates.id to SortOrder.ASC)
                .map { row ->
                    val templateId = row[ConsentTemplates.id]
                    val userCount = UserConsents.select {
                        UserConsents.consentId eq templateId
                    }.count().toLong()
                    
                    AdminConsentTemplateResponse(
                        id = templateId,
                        name = row[ConsentTemplates.name],
                        description = row[ConsentTemplates.description],
                        isMandatory = row[ConsentTemplates.isMandatory],
                        userCount = userCount
                    )
                }
        }
    }
    
    fun getConsentTemplateById(id: Int): AdminConsentTemplateResponse? {
        return dbTransaction {
            ConsentTemplates.select { ConsentTemplates.id eq id }
                .firstOrNull()
                ?.let { row ->
                    val userCount = UserConsents.select {
                        UserConsents.consentId eq id
                    }.count().toLong()
                    
                    AdminConsentTemplateResponse(
                        id = row[ConsentTemplates.id],
                        name = row[ConsentTemplates.name],
                        description = row[ConsentTemplates.description],
                        isMandatory = row[ConsentTemplates.isMandatory],
                        userCount = userCount
                    )
                }
        }
    }
    
    fun createConsentTemplate(request: CreateConsentTemplateRequest): Int {
        return dbTransaction {
            ConsentTemplates.insert {
                it[name] = request.name
                it[description] = request.description
                it[isMandatory] = request.isMandatory
            } get ConsentTemplates.id
        }
    }
    
    fun updateConsentTemplate(id: Int, request: UpdateConsentTemplateRequest): Boolean {
        return dbTransaction {
            val updateCount = ConsentTemplates.update({ ConsentTemplates.id eq id }) {
                request.name?.let { value -> it[ConsentTemplates.name] = value }
                request.description?.let { value -> it[ConsentTemplates.description] = value }
                request.isMandatory?.let { value -> it[ConsentTemplates.isMandatory] = value }
            }
            updateCount > 0
        }
    }
    
    fun deleteConsentTemplate(id: Int): Boolean {
        return dbTransaction {
            val deleteCount = ConsentTemplates.deleteWhere { ConsentTemplates.id eq id }
            deleteCount > 0
        }
    }
}

