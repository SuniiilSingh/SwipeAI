# ==========================================
# Stage 1: Build the JAR with Gradle
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy gradle wrapper and configuration files first for Docker layer caching
COPY gradlew settings.gradle build.gradle /app/
COPY gradle /app/gradle

# Make wrapper executable and warm cache
RUN chmod +x gradlew

# Copy source code
COPY src /app/src

# Build production bootJar without running tests
RUN ./gradlew bootJar -x test --no-daemon

# ==========================================
# Stage 2: Minimal Production JRE Runtime
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for container health checks
RUN apk add --no-cache curl

# Create non-root system user for security
RUN addgroup -S swipeai && adduser -S swipeai -G swipeai

# Create uploads directory and set permissions
RUN mkdir -p /app/uploads && chown -R swipeai:swipeai /app

# Copy compiled jar from builder stage
COPY --from=builder --chown=swipeai:swipeai /app/build/libs/SwipeAI-0.0.1-SNAPSHOT.jar /app/app.jar

# Switch to non-root user
USER swipeai

# Expose backend port
EXPOSE 8080

# Production JVM tuning flags:
# - G1GC for low-latency garbage collection
# - MaxRAMPercentage=75.0 dynamically adapts to container memory limits
# - ExitOnOutOfMemoryError ensures fast restart on memory depletion
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
