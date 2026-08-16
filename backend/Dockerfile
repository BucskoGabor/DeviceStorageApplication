# ===== Backend Dockerfile - Multi-stage build =====
# Stage 1: Build (Maven + Java 21)
# Stage 2: Runtime (Eclipse Temurin JRE 21 + Alpine)

# ===== STAGE 1: Build =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache Maven dependencies (réteg kihasználva a Docker cache-t)
COPY pom.xml checkstyle.xml suppressions.xml ./
RUN mvn dependency:go-offline -B -q

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests -B -q

# ===== STAGE 2: Runtime =====
FROM eclipse-temurin:21-jre-alpine

# Időzóna beállítás
ENV TZ=UTC

# Non-root user (security)
RUN addgroup -S spring && \
    adduser -S spring -G spring && \
    mkdir -p /tmp /var/run /var/uploads /var/backups/archive/audit && \
    chown -R spring:spring /tmp /var/run /var/uploads /var/backups

WORKDIR /app

# Copy JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# JVM tuning + UTC + sane defaults
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -Duser.timezone=UTC"

# Health check (Actuator)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# Run as non-root user
USER spring:spring

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
