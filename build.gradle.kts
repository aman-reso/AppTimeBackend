import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    application
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "com.apptime.code"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:2.3.5")
    implementation("io.ktor:ktor-server-netty:2.3.5")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.5")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.5")
    implementation("io.ktor:ktor-server-auth:2.3.5")
    
    // Database - PostgreSQL with Exposed ORM
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("org.jetbrains.exposed:exposed-core:0.44.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.44.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.44.1")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.44.1")
    implementation("com.zaxxer:HikariCP:5.1.0") // Connection pooling
    
    // Kotlinx DateTime for timestamps
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")
    
    // Base32 encoding/decoding for TOTP secrets
    implementation("commons-codec:commons-codec:1.15")
    
    // Firebase Admin SDK for push notifications
    implementation("com.google.firebase:firebase-admin:9.2.0")
    
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("MainKt")
    applicationDefaultJvmArgs = listOf(
        "-Duser.timezone=Asia/Kolkata",
        "-Duser.country=IN",
        "-Duser.language=en"
    )
}

// Configure Shadow plugin to create fat JAR
tasks.shadowJar {
    archiveBaseName.set("AppTimeBackend")
    archiveClassifier.set("")  // Remove "-all" classifier to make it the main JAR
    manifest {
        attributes(mapOf("Main-Class" to "MainKt"))
    }
}

// Make shadowJar run as part of build
tasks.build {
    dependsOn(tasks.shadowJar)
}

// Task to clear all database tables
tasks.register<JavaExec>("clearAllTables") {
    group = "database"
    description = "Clear all data from all database tables"
    mainClass.set("ClearAllTablesKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// Task to clear only stats tables
tasks.register<JavaExec>("clearStatsTables") {
    group = "database"
    description = "Clear all data from stats tables (focus_mode_stats, leaderboard_stats)"
    mainClass.set("ClearStatsTablesKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// Task to clear usage events table (synced data)
tasks.register<JavaExec>("clearUsageEvents") {
    group = "database"
    description = "Clear all data from app_usage_events table (synced usage data)"
    mainClass.set("ClearUsageEventsKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// Task to clear challenges tables
tasks.register<JavaExec>("clearChallenges") {
    group = "database"
    description = "Clear all data from challenges tables (challenges, challenge_participants, challenge_participant_stats)"
    mainClass.set("ClearChallengesKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// Task to clear all user-related data (keeping challenges)
tasks.register<JavaExec>("clearUserData") {
    group = "database"
    description = "Clear all user-related data (users, usage events, participants, etc.) while keeping challenges list"
    mainClass.set("ClearUserDataKt")
    classpath = sourceSets["main"].runtimeClasspath
}