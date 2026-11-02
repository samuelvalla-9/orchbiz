# syntax=docker/dockerfile:1
# Untested on the build machine (no Docker available there). Build context: platform/lead-api
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 orchbiz
WORKDIR /app
COPY --from=build /src/target/lead-api.jar /app/lead-api.jar
USER orchbiz
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/lead-api.jar"]
