package com.apptime.code.common

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.JarURLConnection
import java.util.concurrent.ConcurrentHashMap

/**
 * Translation message keys
 */
object MessageKeys {
    // Admin messages
    const val ADMIN_LOGIN_SUCCESS = "admin.login.success"
    const val ADMIN_LOGIN_INVALID = "admin.login.invalid"
    const val ADMIN_TOKEN_VALID = "admin.token.valid"
    const val ADMIN_TOKEN_INVALID = "admin.token.invalid"
    const val ADMIN_STATS_RETRIEVED = "admin.stats.retrieved"
    const val ADMIN_STATS_FAILED = "admin.stats.failed"
    
    // Challenge messages
    const val CHALLENGES_RETRIEVED = "challenges.retrieved"
    const val CHALLENGE_RETRIEVED = "challenge.retrieved"
    const val CHALLENGE_CREATED = "challenge.created"
    const val CHALLENGE_UPDATED = "challenge.updated"
    const val CHALLENGE_DELETED = "challenge.deleted"
    const val CHALLENGE_NOT_FOUND = "challenge.not_found"
    const val CHALLENGES_FAILED = "challenges.failed"
    
    // User messages
    const val USERS_RETRIEVED = "users.retrieved"
    const val USER_RETRIEVED = "user.retrieved"
    const val USER_UPDATED = "user.updated"
    const val USER_BLOCKED = "user.blocked"
    const val USER_UNBLOCKED = "user.unblocked"
    const val USER_DELETED = "user.deleted"
    const val USER_NOT_FOUND = "user.not_found"
    const val USERS_FAILED = "users.failed"
    const val COINS_ADDED = "coins.added"
    
    // Reward messages
    const val REWARDS_RETRIEVED = "rewards.retrieved"
    const val REWARD_RETRIEVED = "reward.retrieved"
    const val REWARDS_FAILED = "rewards.failed"
    const val REWARD_NOT_FOUND = "reward.not_found"
    
    // Consent messages
    const val CONSENT_TEMPLATES_RETRIEVED = "consent.templates.retrieved"
    const val CONSENT_TEMPLATE_RETRIEVED = "consent.template.retrieved"
    const val CONSENT_TEMPLATE_CREATED = "consent.template.created"
    const val CONSENT_TEMPLATE_UPDATED = "consent.template.updated"
    const val CONSENT_TEMPLATE_DELETED = "consent.template.deleted"
    const val CONSENT_TEMPLATE_NOT_FOUND = "consent.template.not_found"
    const val CONSENT_TEMPLATES_FAILED = "consent.templates.failed"
    
    // Catalog messages
    const val CATALOG_RETRIEVED = "catalog.retrieved"
    const val CATALOG_ITEM_RETRIEVED = "catalog.item.retrieved"
    const val CATALOG_ITEM_CREATED = "catalog.item.created"
    const val CATALOG_ITEM_UPDATED = "catalog.item.updated"
    const val CATALOG_FAILED = "catalog.failed"
    
    // Transaction messages
    const val TRANSACTIONS_RETRIEVED = "transactions.retrieved"
    const val TRANSACTION_RETRIEVED = "transaction.retrieved"
    const val TRANSACTION_STATUS_UPDATED = "transaction.status.updated"
    const val TRANSACTION_NOT_FOUND = "transaction.not_found"
    const val TRANSACTIONS_FAILED = "transactions.failed"
    
    // User/Profile messages
    const val DEVICE_REGISTERED = "user.device.registered"
    const val REGISTRATION_FAILED = "user.registration.failed"
    const val USERS_FOUND = "user.search.found"
    const val SEARCH_FAILED = "user.search.failed"
    const val PROFILE_RETRIEVED = "user.profile.retrieved"
    const val PROFILE_UPDATED = "user.profile.updated"
    const val PROFILE_UPDATE_FAILED = "user.profile.update_failed"
    const val USERNAME_UPDATED = "user.username.updated"
    const val USERNAME_UPDATE_FAILED = "user.username.update_failed"
    const val SYNC_STATUS_RETRIEVED = "user.sync.status.retrieved"
    const val SYNC_STATUS_FAILED = "user.sync.status.failed"
    
    // TOTP messages
    const val TOTP_GENERATED = "totp.generated"
    const val TOTP_GENERATE_FAILED = "totp.generate.failed"
    const val TOTP_VERIFIED = "totp.verified"
    const val TOTP_VERIFY_FAILED = "totp.verify.failed"
    const val TOTP_ACCESS_STATUS_RETRIEVED = "totp.access.status.retrieved"
    const val TOTP_ACCESS_STATUS_FAILED = "totp.access.status.failed"
    const val TOTP_SESSIONS_RETRIEVED = "totp.sessions.retrieved"
    const val TOTP_SESSIONS_FAILED = "totp.sessions.failed"
    const val TOTP_CONTROL_PANEL_RETRIEVED = "totp.control_panel.retrieved"
    const val TOTP_CONTROL_PANEL_FAILED = "totp.control_panel.failed"
    const val TOTP_ACCESS_GRANTED = "totp.access.granted"
    const val TOTP_ACCESS_GRANT_FAILED = "totp.access.grant.failed"
    const val TOTP_ACCESS_REVOKED = "totp.access.revoked"
    const val TOTP_ACCESS_REVOKE_FAILED = "totp.access.revoke.failed"
    const val TOTP_ACCESS_EXTENDED = "totp.access.extended"
    const val TOTP_ACCESS_EXTEND_FAILED = "totp.access.extend.failed"
    const val TOTP_NO_SESSION = "totp.no_session"
    
    // Challenge user messages
    const val ACTIVE_CHALLENGES_RETRIEVED = "challenge.active.retrieved"
    const val ACTIVE_CHALLENGES_FAILED = "challenge.active.failed"
    const val CHALLENGE_JOINED = "challenge.joined"
    const val CHALLENGE_JOIN_FAILED = "challenge.join.failed"
    const val USER_CHALLENGES_RETRIEVED = "challenge.user.retrieved"
    const val USER_CHALLENGES_FAILED = "challenge.user.failed"
    const val CHALLENGE_DETAILS_RETRIEVED = "challenge.details.retrieved"
    const val CHALLENGE_DETAILS_FAILED = "challenge.details.failed"
    const val CHALLENGE_STATS_SUBMITTED = "challenge.stats.submitted"
    const val CHALLENGE_STATS_FAILED = "challenge.stats.failed"
    const val CHALLENGE_RANKINGS_RETRIEVED = "challenge.rankings.retrieved"
    const val CHALLENGE_RANKINGS_FAILED = "challenge.rankings.failed"
    const val CHALLENGE_LAST_SYNC_RETRIEVED = "challenge.last_sync.retrieved"
    const val CHALLENGE_LAST_SYNC_FAILED = "challenge.last_sync.failed"
    
    // Reward user messages
    const val USER_REWARDS_RETRIEVED = "reward.user.retrieved"
    const val USER_REWARDS_FAILED = "reward.user.failed"
    const val REWARD_SUMMARY_RETRIEVED = "reward.summary.retrieved"
    const val REWARD_SUMMARY_FAILED = "reward.summary.failed"
    const val REWARD_CLAIMED = "reward.claimed"
    const val REWARD_CLAIM_FAILED = "reward.claim.failed"
    const val REWARD_CREATED = "reward.created"
    const val REWARD_CREATE_FAILED = "reward.create.failed"
    const val CHALLENGE_REWARDS_AWARDED = "reward.challenge.awarded"
    const val CHALLENGE_REWARDS_AWARD_FAILED = "reward.challenge.award.failed"
    const val CHALLENGE_REWARDS_RETRIEVED = "reward.challenge.retrieved"
    const val CHALLENGE_REWARDS_FAILED = "reward.challenge.failed"
    const val USER_COINS_RETRIEVED = "reward.coins.retrieved"
    const val USER_COINS_FAILED = "reward.coins.failed"
    const val COINS_ADDED_USER = "reward.coins.added"
    const val COINS_ADD_FAILED = "reward.coins.add.failed"
    const val CATALOG_ITEM_CLAIMED = "reward.catalog.claimed"
    const val CATALOG_CLAIM_FAILED = "reward.catalog.claim.failed"
    const val CANNOT_CLAIM_REWARD = "reward.cannot_claim"
    
    // Consent user messages
    const val USER_CONSENTS_RETRIEVED = "consent.user.retrieved"
    const val USER_CONSENTS_FAILED = "consent.user.failed"
    const val CONSENTS_SUBMITTED = "consent.submitted"
    const val CONSENTS_SUBMIT_FAILED = "consent.submit.failed"
    
    // Usage messages
    const val USAGE_EVENT_SUBMITTED = "usage.event.submitted"
    const val USAGE_EVENT_FAILED = "usage.event.failed"
    const val BATCH_EVENTS_SUBMITTED = "usage.batch.submitted"
    const val BATCH_EVENTS_FAILED = "usage.batch.failed"
    const val ALL_USERS_RETRIEVED = "usage.users.retrieved"
    const val ALL_USERS_FAILED = "usage.users.failed"
    const val DAILY_STATS_RETRIEVED = "usage.daily.retrieved"
    const val DAILY_STATS_FAILED = "usage.daily.failed"
    const val DEBUG_INFO_RETRIEVED = "usage.debug.retrieved"
    const val DEBUG_INFO_FAILED = "usage.debug.failed"
    const val RAW_EVENTS_RETRIEVED = "usage.raw.retrieved"
    const val RAW_EVENTS_FAILED = "usage.raw.failed"
    const val LAST_SYNC_RETRIEVED = "usage.last_sync.retrieved"
    const val LAST_SYNC_FAILED = "usage.last_sync.failed"
    const val USER_EVENTS_DELETED = "usage.events.deleted"
    const val USER_EVENTS_DELETE_FAILED = "usage.events.delete.failed"
    
    // Location messages
    const val LOCATION_SYNCED = "location.synced"
    const val LOCATION_SYNC_FAILED = "location.sync.failed"
    const val LOCATION_RETRIEVED = "location.retrieved"
    const val LOCATION_FAILED = "location.failed"
    const val ACCESS_DENIED = "location.access_denied"
    
    // Leaderboard messages
    const val DAILY_LEADERBOARD_RETRIEVED = "leaderboard.daily.retrieved"
    const val DAILY_LEADERBOARD_FAILED = "leaderboard.daily.failed"
    const val WEEKLY_LEADERBOARD_RETRIEVED = "leaderboard.weekly.retrieved"
    const val WEEKLY_LEADERBOARD_FAILED = "leaderboard.weekly.failed"
    const val MONTHLY_LEADERBOARD_RETRIEVED = "leaderboard.monthly.retrieved"
    const val MONTHLY_LEADERBOARD_FAILED = "leaderboard.monthly.failed"
    const val LEADERBOARD_SYNCED = "leaderboard.synced"
    const val LEADERBOARD_SYNC_FAILED = "leaderboard.sync.failed"
    const val LEADERBOARD_STATS_UPDATED = "leaderboard.stats.updated"
    const val LEADERBOARD_STATS_UPDATE_FAILED = "leaderboard.stats.update.failed"
    const val SCREEN_TIME_RETRIEVED = "leaderboard.screentime.retrieved"
    const val SCREEN_TIME_FAILED = "leaderboard.screentime.failed"
    
    // Focus messages
    const val FOCUS_SESSION_SUBMITTED = "focus.session.submitted"
    const val FOCUS_SESSIONS_SUBMITTED = "focus.sessions.submitted"
    const val FOCUS_SUBMIT_FAILED = "focus.submit.failed"
    const val FOCUS_HISTORY_RETRIEVED = "focus.history.retrieved"
    const val FOCUS_HISTORY_RANGE_RETRIEVED = "focus.history.range.retrieved"
    const val FOCUS_HISTORY_FAILED = "focus.history.failed"
    const val FOCUS_STATS_RETRIEVED = "focus.stats.retrieved"
    const val FOCUS_STATS_FAILED = "focus.stats.failed"
    const val FOCUS_MODE_STATS_SAVED = "focus.mode.stats.saved"
    const val FOCUS_MODE_STATS_SAVE_FAILED = "focus.mode.stats.save.failed"
    const val FOCUS_MODE_STATS_RETRIEVED = "focus.mode.stats.retrieved"
    const val FOCUS_MODE_STATS_RANGE_RETRIEVED = "focus.mode.stats.range.retrieved"
    const val FOCUS_MODE_STATS_FROM_RETRIEVED = "focus.mode.stats.from.retrieved"
    const val FOCUS_MODE_STATS_UNTIL_RETRIEVED = "focus.mode.stats.until.retrieved"
    const val FOCUS_MODE_STATS_FAILED = "focus.mode.stats.failed"
    
    // App Stats messages
    const val APP_STATS_ADDED = "app.stats.added"
    const val APP_STATS_ADD_FAILED = "app.stats.add.failed"
    const val APP_STATS_UPDATED = "app.stats.updated"
    const val APP_STATS_UPDATE_FAILED = "app.stats.update.failed"
    const val APP_STATS_RETRIEVED = "app.stats.retrieved"
    const val APP_STATS_FAILED = "app.stats.failed"
    
    // Feature flags messages
    const val FEATURE_FLAGS_RETRIEVED = "feature.flags.retrieved"
    const val FEATURE_FLAGS_FAILED = "feature.flags.failed"
    const val FEATURE_FLAG_RETRIEVED = "feature.flag.retrieved"
    const val FEATURE_FLAG_NOT_FOUND = "feature.flag.not_found"
    const val FEATURE_FLAG_CREATED = "feature.flag.created"
    const val FEATURE_FLAG_CREATE_FAILED = "feature.flag.create.failed"
    const val FEATURE_FLAG_UPDATED = "feature.flag.updated"
    const val FEATURE_FLAG_UPDATE_FAILED = "feature.flag.update.failed"
    const val FEATURE_FLAG_DELETED = "feature.flag.deleted"
    const val FEATURE_FLAG_DELETE_FAILED = "feature.flag.delete.failed"
    
    // Common error messages
    const val INVALID_REQUEST = "error.invalid_request"
    const val INTERNAL_SERVER_ERROR = "error.internal_server_error"
    const val UNAUTHORIZED = "error.unauthorized"
    const val NOT_FOUND = "error.not_found"
    const val BAD_REQUEST = "error.bad_request"
    const val FORBIDDEN = "error.forbidden"
    const val INVALID_JSON = "error.invalid_json"
}

/**
 * Translation data structure
 */
@Serializable
data class Translations(
    val messages: Map<String, String> = emptyMap()
)

/**
 * Translation Service to load and manage translations
 */
object TranslationService {
    private val translationsCache = ConcurrentHashMap<String, Translations>()
    private val json = Json { ignoreUnknownKeys = true }
    private const val DEFAULT_LANGUAGE = "en"
    private const val TRANSLATIONS_DIR = "translations"
    
    init {
        loadTranslations()
    }
    
    /**
     * Load all translation files from resources
     */
    private fun loadTranslations() {
        try {
            // First try loading from file system (for development)
            val translationsDir = File("src/main/resources/$TRANSLATIONS_DIR")
            if (translationsDir.exists() && translationsDir.isDirectory) {
                translationsDir.listFiles { _, name -> name.endsWith(".json") }?.forEach { file ->
                    val language = file.nameWithoutExtension
                    try {
                        val content = file.readText()
                        val translations = json.decodeFromString<Translations>(content)
                        translationsCache[language] = translations
                    } catch (e: Exception) {
                        println("Failed to load translation file ${file.name}: ${e.message}")
                    }
                }
            }
            
            // Load from classpath resources (works in JAR and development)
            val classLoader = Thread.currentThread().contextClassLoader ?: TranslationService::class.java.classLoader
            try {
                val resourceStream = classLoader.getResources(TRANSLATIONS_DIR)
                while (resourceStream.hasMoreElements()) {
                    val url = resourceStream.nextElement()
                    when (url.protocol) {
                        "jar" -> {
                            // Handle JAR resources
                            try {
                                val jarConnection = url.openConnection() as JarURLConnection
                                val jarFile = jarConnection.jarFile
                                val entries = jarFile.entries()
                                
                                while (entries.hasMoreElements()) {
                                    val entry = entries.nextElement()
                                    if (entry.name.startsWith("$TRANSLATIONS_DIR/") && entry.name.endsWith(".json") && !entry.isDirectory) {
                                        val language = entry.name.substringAfterLast("/").substringBeforeLast(".")
                                        if (!translationsCache.containsKey(language)) {
                                            try {
                                                jarFile.getInputStream(entry).use { inputStream ->
                                                    val content = inputStream.bufferedReader().use { it.readText() }
                                                    val translations = json.decodeFromString<Translations>(content)
                                                    translationsCache[language] = translations
                                                }
                                            } catch (e: Exception) {
                                                println("Failed to load translation from JAR ${entry.name}: ${e.message}")
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                println("Failed to process JAR URL: ${e.message}")
                            }
                        }
                        "file" -> {
                            // Handle file system resources (development)
                            try {
                                val resourcePath = url.path
                                val resourceDir = File(resourcePath)
                                if (resourceDir.exists() && resourceDir.isDirectory) {
                                    resourceDir.listFiles { _, name -> name.endsWith(".json") }?.forEach { file ->
                                        val language = file.nameWithoutExtension
                                        if (!translationsCache.containsKey(language)) {
                                            try {
                                                val content = file.readText()
                                                val translations = json.decodeFromString<Translations>(content)
                                                translationsCache[language] = translations
                                            } catch (e: Exception) {
                                                println("Failed to load translation file ${file.name}: ${e.message}")
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                println("Failed to process file URL: ${e.message}")
                            }
                        }
                        else -> {
                            // Unknown protocol, skip
                        }
                    }
                }
            } catch (e: Exception) {
                println("Failed to load translations from classpath: ${e.message}")
            }
            
            // Fallback: try direct resource loading (most reliable method)
            // All supported languages
            val knownLanguages = listOf(
                // Default/English
                "en",
                // Indian Languages
                "hi", "bn", "ta", "te", "mr", "gu", "kn", "ml", "pa", "ur",
                // European Languages
                "fr", "es", "de", "it", "nl", "pt", "pt-rBR", "ru",
                // Asian Languages
                "zh-rCN", "zh-rTW", "ja", "ko", "id", "th", "vi",
                // Middle Eastern Languages
                "ar", "iw", "fa"
            )
            knownLanguages.forEach { lang ->
                if (!translationsCache.containsKey(lang)) {
                    try {
                        val resource = classLoader.getResourceAsStream("$TRANSLATIONS_DIR/$lang.json")
                        if (resource != null) {
                            val content = resource.bufferedReader().use { it.readText() }
                            val translations = json.decodeFromString<Translations>(content)
                            translationsCache[lang] = translations
                            println("Loaded translation file: $lang.json via resource stream")
                        }
                    } catch (e: Exception) {
                        println("Failed to load $lang.json via resource stream: ${e.message}")
                    }
                }
            }
            
            if (translationsCache.isNotEmpty()) {
                println("✓ TranslationService: Loaded translations for languages: ${translationsCache.keys.joinToString(", ")}")
                translationsCache.forEach { (lang, translations) ->
                    println("  - $lang: ${translations.messages.size} messages loaded")
                }
            } else {
                println("✗ WARNING: TranslationService: No translations loaded! API responses will use default messages.")
                println("  Check if translation files exist in: src/main/resources/translations/")
            }
        } catch (e: Exception) {
            println("Error loading translations: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Get translated message for a given key and language
     * @param key Message key
     * @param language Language code (e.g., "en", "es", "fr")
     * @param defaultMessage Default message if translation not found
     * @return Translated message or default message
     */
    fun getMessage(key: String, language: String? = null, defaultMessage: String? = null): String {
        val lang = language?.lowercase() ?: DEFAULT_LANGUAGE
        
        // Debug: Log if translations are empty
        if (translationsCache.isEmpty()) {
            println("WARNING: TranslationService cache is empty! Translations may not have loaded.")
        }
        
        // Try exact language match first (handles locale variants like "pt-rBR", "zh-rCN")
        var translations = translationsCache[lang]
        var message = translations?.messages?.get(key)
        if (message != null) {
            return message
        }
        
        // If exact match not found and language has a variant (e.g., "pt-rBR" -> "pt", "zh-rCN" -> "zh")
        // Try base language code
        if (lang.contains("-")) {
            val baseLang = lang.substringBefore("-")
            translations = translationsCache[baseLang]
            message = translations?.messages?.get(key)
            if (message != null) {
                return message
            }
        }
        
        // Try default language if current language doesn't have the key
        if (lang != DEFAULT_LANGUAGE) {
            val defaultTranslations = translationsCache[DEFAULT_LANGUAGE]
            val defaultMsg = defaultTranslations?.messages?.get(key)
            if (defaultMsg != null) {
                return defaultMsg
            }
        }
        
        // Debug: Log missing translation key
        println("WARNING: Translation key '$key' not found for language '$lang'. Available keys: ${translations?.messages?.keys?.take(5)?.joinToString(", ")}")
        
        // Return provided default message or the key itself
        return defaultMessage ?: key
    }
    
    /**
     * Get all available languages
     */
    fun getAvailableLanguages(): Set<String> {
        return translationsCache.keys.toSet()
    }
    
    /**
     * Check if a language is supported
     */
    fun isLanguageSupported(language: String): Boolean {
        return translationsCache.containsKey(language.lowercase())
    }
}

