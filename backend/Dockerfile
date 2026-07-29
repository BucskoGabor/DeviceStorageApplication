# Backend Dockerfile - Multi-stage build
# Build stage: Maven + Java 21
# Runtime stage: Eclipse Temurin JRE 21 Alpine

# ===== STAGE 1: Build =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B -q

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests -B -q

# ===== STAGE 2: Runtime =====
FROM eclipse-temurin:21-jre-alpine

# Non-root user
RUN addgroup -S spring && \
    adduser -S spring -G spring && \
    mkdir -p /tmp /var/run /var/uploads /var/backups/archive/audit && \
    chown -R spring:spring /tmp /var/run /var/uploads /var/backups

WORKDIR /app

# Copy JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Health check (used by docker-compose)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q 'UP' || exit 1

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-dev}", \
    "-jar", \
    "/app/app.jar"]