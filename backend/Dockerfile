# Backend Dockerfile - PLACEHOLDER
# A tényleges implementáció a Task 1.2-ben történik (Spring Boot projekt init).
#
# Várható struktúra (Task 1.2):
#   FROM maven:3.9-eclipse-temurin-21 AS build
#   WORKDIR /app
#   COPY pom.xml .
#   RUN mvn dependency:go-offline -B
#   COPY src ./src
#   RUN mvn package -DskipTests -B
#
#   FROM eclipse-temurin:21-jre-alpine
#   RUN addgroup -S spring && adduser -S spring -G spring
#   WORKDIR /app
#   COPY --from=build /app/target/*.jar app.jar
#   USER spring:spring
#   EXPOSE 8080
#   ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Jelenlegi placeholder, hogy a docker-compose build ne fail-eljen
# A Task 1.2-ben lesz kicserélve a fenti többszintű Dockerfile-ra
FROM alpine:3.19
RUN echo "Backend Dockerfile placeholder — implementálás a Task 1.2-ben"
CMD ["echo", "Backend not yet implemented (Task 1.2)"]