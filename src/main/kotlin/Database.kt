import com.apptime.code.blockeddomains.BlockedDomainGroups
import com.apptime.code.blockeddomains.BlockedDomains
import com.apptime.code.challenges.ChallengeParticipantStats
import com.apptime.code.challenges.ChallengeParticipants
import com.apptime.code.challenges.ChallengeSeedData
import com.apptime.code.challenges.Challenges
import com.apptime.code.common.EnvLoader
import com.apptime.code.consents.ConsentSeedData
import com.apptime.code.consents.ConsentTemplates
import com.apptime.code.consents.UserConsents
import com.apptime.code.focus.FocusSessions
import com.apptime.code.focus.FocusModeStats
import com.apptime.code.leaderboard.LeaderboardStats
import com.apptime.code.notifications.Notifications
import com.apptime.code.users.Users
import users.TOTPVerificationSessions
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import urlsearch.UrlSearches
import usage.AppUsageEvents

object DatabaseFactory {
    fun init() {
        // Load .env file if it exists (this reads from .env file)
        EnvLoader.loadEnvFile()
        
        // Get environment variables from .env file or system environment
        val jdbcUrl = EnvLoader.getEnv("DATABASE_URL")
            ?: "jdbc:postgresql://localhost:5432/screentime_db"
        val dbUser = EnvLoader.getEnv("DB_USER") ?: "postgres"
        val dbPassword = EnvLoader.getEnv("DB_PASSWORD") ?: "Sharma@11"

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            username = dbUser
            password = dbPassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            minimumIdle = 2
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        val dataSource = HikariDataSource(config)

        Database.connect(
            dataSource,
            databaseConfig = DatabaseConfig {
                defaultRepetitionAttempts = 3
            }
        )

        // Create all tables
        createTables()
        
        // Seed initial data
        seedInitialData()

        println("✅ Database connected successfully!")
    }

    private fun createTables() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                // Users module
                Users,
                TOTPVerificationSessions,

                // Usage module
                AppUsageEvents,

                // Focus module
                FocusSessions,
                FocusModeStats,

                // URL Search module
                UrlSearches,

                // Blocked Domains module
                BlockedDomainGroups,
                BlockedDomains,

                // Notifications module
                Notifications,
                
                // Consents module
                ConsentTemplates,
                UserConsents,
                
                // Leaderboard module
                LeaderboardStats,
                
                // Challenges module
                Challenges,
                ChallengeParticipants,
                ChallengeParticipantStats
            )
        }
        println("✅ Database tables created/verified!")
    }
    
    private fun seedInitialData() {
        // Seed consent templates
        ConsentSeedData.seedConsentTemplates()
        
        // Seed challenges
        ChallengeSeedData.seedChallenges()
    }
}
