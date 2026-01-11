package com.apptime.code.admin

import com.apptime.code.challenges.AppDetail
import com.apptime.code.challenges.ChallengeParticipants
import com.apptime.code.challenges.Challenges
import com.apptime.code.common.dbTransaction
import com.apptime.code.consents.ConsentTemplates
import com.apptime.code.consents.UserConsents
import com.apptime.code.rewards.Rewards
import com.apptime.code.users.Users
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class AdminRepository {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Parse appdetail JSON string to List<AppDetail>
     */
    private fun parseAppDetail(appdetailJson: String?): List<AppDetail>? {
        if (appdetailJson.isNullOrBlank()) {
            return null
        }
        return try {
            json.decodeFromString<List<AppDetail>>(appdetailJson)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Serialize List<AppDetail> to JSON string
     */
    private fun serializeAppDetail(appdetail: List<AppDetail>?): String? {
        if (appdetail == null || appdetail.isEmpty()) {
            return null
        }
        return try {
            json.encodeToString(appdetail)
        } catch (e: Exception) {
            null
        }
    }
    
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
                        colorScheme = row[Challenges.colorScheme],
                        variant = row[Challenges.variant],
                        appdetail = parseAppDetail(row[Challenges.appdetail]),
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
                        colorScheme = row[Challenges.colorScheme],
                        variant = row[Challenges.variant],
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
                it[colorScheme] = request.colorScheme
                it[variant] = request.variant
                it[appdetail] = serializeAppDetail(request.appdetail)
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
                request.colorScheme?.let { value -> it[Challenges.colorScheme] = value }
                request.variant?.let { value -> it[Challenges.variant] = value }
                request.appdetail?.let { value -> it[Challenges.appdetail] = serializeAppDetail(value) }
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
                        isBlocked = row[Users.isBlocked],
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
                        isBlocked = row[Users.isBlocked],
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
                request.isBlocked?.let { value -> it[Users.isBlocked] = value }
            }
            updateCount > 0
        }
    }
    
    fun blockUser(userId: String): Boolean {
        return dbTransaction {
            val updateCount = Users.update({ Users.userId eq userId }) {
                it[Users.isBlocked] = true
            }
            updateCount > 0
        }
    }
    
    fun unblockUser(userId: String): Boolean {
        return dbTransaction {
            val updateCount = Users.update({ Users.userId eq userId }) {
                it[Users.isBlocked] = false
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
    
    // Reward Management
    fun getAllRewards(limit: Int = 100, offset: Int = 0): List<AdminRewardResponse> {
        return dbTransaction {
            Rewards.selectAll()
                .orderBy(Rewards.earnedAt to SortOrder.DESC)
                .limit(limit, offset.toLong())
                .map { row ->
                    AdminRewardResponse(
                        id = row[Rewards.id],
                        userId = row[Rewards.userId],
                        type = row[Rewards.type],
                        source = row[Rewards.rewardSource],
                        title = row[Rewards.title],
                        description = row[Rewards.description],
                        amount = row[Rewards.amount],
                        metadata = row[Rewards.metadata],
                        challengeId = row[Rewards.challengeId],
                        challengeTitle = row[Rewards.challengeTitle],
                        rank = row[Rewards.rank],
                        earnedAt = row[Rewards.earnedAt].toString(),
                        isClaimed = row[Rewards.isClaimed],
                        claimedAt = row[Rewards.claimedAt]?.toString()
                    )
                }
        }
    }
    
    fun getRewardById(id: Long): AdminRewardResponse? {
        return dbTransaction {
            Rewards.select { Rewards.id eq id }
                .firstOrNull()
                ?.let { row ->
                    AdminRewardResponse(
                        id = row[Rewards.id],
                        userId = row[Rewards.userId],
                        type = row[Rewards.type],
                        source = row[Rewards.rewardSource],
                        title = row[Rewards.title],
                        description = row[Rewards.description],
                        amount = row[Rewards.amount],
                        metadata = row[Rewards.metadata],
                        challengeId = row[Rewards.challengeId],
                        challengeTitle = row[Rewards.challengeTitle],
                        rank = row[Rewards.rank],
                        earnedAt = row[Rewards.earnedAt].toString(),
                        isClaimed = row[Rewards.isClaimed],
                        claimedAt = row[Rewards.claimedAt]?.toString()
                    )
                }
        }
    }
    
    fun createReward(request: CreateRewardRequest): Long {
        return dbTransaction {
            Rewards.insert {
                it[Rewards.userId] = request.userId
                it[Rewards.type] = request.type
                it[Rewards.rewardSource] = request.source
                it[Rewards.title] = request.title
                it[Rewards.description] = request.description
                it[Rewards.amount] = request.amount
                it[Rewards.metadata] = request.metadata
                it[Rewards.challengeId] = request.challengeId
                it[Rewards.challengeTitle] = request.challengeTitle
                it[Rewards.rank] = request.rank
            } get Rewards.id
        }
    }
    
    fun updateReward(id: Long, request: UpdateRewardRequest): Boolean {
        return dbTransaction {
            // First, get the current reward to check its claimed status
            val currentReward = Rewards.select { Rewards.id eq id }.firstOrNull()
            
            val updateCount = Rewards.update({ Rewards.id eq id }) {
                request.userId?.let { value -> it[Rewards.userId] = value }
                request.type?.let { value -> it[Rewards.type] = value }
                request.source?.let { value -> it[Rewards.rewardSource] = value }
                request.title?.let { value -> it[Rewards.title] = value }
                request.description?.let { value -> it[Rewards.description] = value }
                request.amount?.let { value -> it[Rewards.amount] = value }
                request.metadata?.let { value -> it[Rewards.metadata] = value }
                request.challengeId?.let { value -> it[Rewards.challengeId] = value }
                request.challengeTitle?.let { value -> it[Rewards.challengeTitle] = value }
                request.rank?.let { value -> it[Rewards.rank] = value }
                request.isClaimed?.let { value -> 
                    it[Rewards.isClaimed] = value
                    if (value && currentReward?.get(Rewards.claimedAt) == null) {
                        it[Rewards.claimedAt] = kotlinx.datetime.Clock.System.now()
                    } else if (!value) {
                        it[Rewards.claimedAt] = null
                    }
                }
            }
            updateCount > 0
        }
    }
    
    fun deleteReward(id: Long): Boolean {
        return dbTransaction {
            val deleteCount = Rewards.deleteWhere { Rewards.id eq id }
            deleteCount > 0
        }
    }
}

