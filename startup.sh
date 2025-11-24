#!/bin/bash

# Startup script for AppTimeBackend on GCP
# This script builds and runs the Kotlin/Ktor application

set -e  # Exit on any error

echo "🚀 Starting AppTimeBackend deployment..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored messages
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if required environment variables are set
check_env_vars() {
    print_info "Checking environment variables..."
    
    if [ -z "$DATABASE_URL" ]; then
        print_warn "DATABASE_URL not set, using default: jdbc:postgresql://localhost:5432/screentime_db"
    else
        print_info "DATABASE_URL is set"
    fi
    
    if [ -z "$DB_USER" ]; then
        print_warn "DB_USER not set, using default: postgres"
    else
        print_info "DB_USER is set"
    fi
    
    if [ -z "$DB_PASSWORD" ]; then
        print_error "DB_PASSWORD is not set! This may cause connection issues."
    else
        print_info "DB_PASSWORD is set"
    fi
}

# Make gradlew executable if it exists
setup_gradle() {
    if [ -f "./gradlew" ]; then
        print_info "Making gradlew executable..."
        chmod +x ./gradlew
    else
        print_error "gradlew not found! Make sure you're in the project root."
        exit 1
    fi
}

# Build the application
build_app() {
    print_info "Building application with Gradle..."
    
    # Use gradlew if available, otherwise use system gradle
    if [ -f "./gradlew" ]; then
        ./gradlew clean build --no-daemon
    else
        print_error "gradlew not found!"
        exit 1
    fi
    
    if [ $? -eq 0 ]; then
        print_info "✅ Build completed successfully!"
    else
        print_error "❌ Build failed!"
        exit 1
    fi
}

# Extract JAR file path
get_jar_path() {
    # Find the JAR file in build/libs directory
    # Look for fat JAR first (with all dependencies), then regular JAR
    JAR_FILE=$(find build/libs -name "*.jar" -not -name "*-plain.jar" | head -n 1)
    
    if [ -z "$JAR_FILE" ]; then
        # If no JAR found, return empty to use gradle run instead
        return 1
    fi
    
    print_info "Found JAR file: $JAR_FILE"
    echo "$JAR_FILE"
    return 0
}

# Run the application using JAR
run_app_jar() {
    local JAR_FILE=$1
    
    # Set JVM options for GCP
    JAVA_OPTS="${JAVA_OPTS:-}"
    
    # Memory settings (adjust based on your GCP instance)
    if [ -z "$JAVA_OPTS" ]; then
        # Default memory settings for Cloud Run
        JAVA_OPTS="-Xmx512m -Xms256m"
    fi
    
    # GCP Cloud Run requires the app to listen on the port specified by PORT env var
    PORT=${PORT:-8080}
    export PORT
    
    print_info "Starting server on port $PORT..."
    print_info "JVM Options: $JAVA_OPTS"
    print_info "Database URL: ${DATABASE_URL:-jdbc:postgresql://localhost:5432/screentime_db}"
    
    # Run the application
    exec java $JAVA_OPTS -jar "$JAR_FILE"
}

# Run the application using Gradle (fallback if no JAR)
run_app_gradle() {
    print_info "No JAR file found, running with Gradle..."
    
    # GCP Cloud Run requires the app to listen on the port specified by PORT env var
    PORT=${PORT:-8080}
    export PORT
    
    print_info "Starting server on port $PORT..."
    print_info "Database URL: ${DATABASE_URL:-jdbc:postgresql://localhost:5432/screentime_db}"
    
    # Run the application using Gradle
    exec ./gradlew run --no-daemon
}

# Run the application
run_app() {
    print_info "Starting application..."
    
    # Try to get JAR file
    if JAR_FILE=$(get_jar_path); then
        run_app_jar "$JAR_FILE"
    else
        print_warn "No executable JAR found. Using Gradle run instead."
        print_warn "For production, consider adding Shadow plugin to create a fat JAR."
        run_app_gradle
    fi
}

# Main execution
main() {
    print_info "========================================="
    print_info "AppTimeBackend Startup Script"
    print_info "========================================="
    
    # Check environment variables
    check_env_vars
    
    # Setup Gradle
    setup_gradle
    
    # Build the application
    build_app
    
    # Run the application
    run_app
}

# Run main function
main

