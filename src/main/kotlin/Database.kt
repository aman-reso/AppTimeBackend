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
import com.apptime.code.features.FeatureFlags
import com.apptime.code.focus.FocusSessions
import com.apptime.code.focus.FocusModeStats
import com.apptime.code.leaderboard.LeaderboardStats
import com.apptime.code.appstats.AppStats
import com.apptime.code.location.UserLocations
import com.apptime.code.notifications.Notifications
import com.apptime.code.rewards.Coins
import com.apptime.code.rewards.Rewards
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

        // Print connection details for debugging (mask password)
        println("🔌 Attempting to connect to database...")
        println("   URL: $jdbcUrl")
        println("   User: $dbUser")
        println("   Password: ${if (dbPassword.isNotEmpty()) "***" else "(empty)"}")

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            username = dbUser
            password = dbPassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 10
            minimumIdle = 2
            isAutoCommit = true  // Exposed manages transactions, so connections should be in auto-commit mode
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            
            // Connection timeout settings
            connectionTimeout = 10000 // 10 seconds
            validationTimeout = 5000 // 5 seconds
            idleTimeout = 600000 // 10 minutes
            maxLifetime = 1800000 // 30 minutes
            
            // Connection test query
            connectionTestQuery = "SELECT 1"
            
            // Leak detection
            leakDetectionThreshold = 60000 // 1 minute
            
            validate()
        }

        try {
            val dataSource = HikariDataSource(config)
            
            // Test the connection immediately
            dataSource.connection.use { conn ->
                if (conn.isValid(5)) {
                    println("✅ Database connection test successful!")
                } else {
                    throw Exception("Connection validation failed")
                }
            }

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
        } catch (e: Exception) {
            println("❌ Database connection failed!")
            println("   Error: ${e.message}")
            println("   Cause: ${e.cause?.message ?: "Unknown"}")
            
            // Provide helpful troubleshooting information
            println("\n🔍 Troubleshooting steps:")
            println("   1. Check if PostgreSQL is running:")
            println("      - macOS: brew services list | grep postgresql")
            println("      - Linux: sudo systemctl status postgresql")
            println("      - Docker: docker ps | grep postgres")
            println("   2. Verify database exists:")
            println("      psql -U $dbUser -l | grep screentime_db")
            println("   3. Test connection manually:")
            println("      psql -U $dbUser -d screentime_db -h localhost")
            println("   4. Check connection details:")
            println("      URL: $jdbcUrl")
            println("      User: $dbUser")
            println("   5. Create .env file with correct credentials if needed")
            
            // Re-throw to prevent application from starting with broken database
            throw RuntimeException("Failed to connect to database. See error details above.", e)
        }
    }

    private fun createTables() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                // Users module
                Users,
                TOTPVerificationSessions,

                // Location module
                UserLocations,

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
                ChallengeParticipantStats,
                
                // Rewards module
                Rewards,
                Coins,
                
                // Features module
                FeatureFlags,
                
                // App Stats module
                AppStats
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
