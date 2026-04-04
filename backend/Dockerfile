# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Run
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/indian-traffic-engine-1.0-SNAPSHOT.jar app.jar

# Render assigns PORT dynamically; Spring reads it via ${PORT:8080}
EXPOSE 8080

# JVM tuning for containerized environments (Render free = 512MB RAM)
CMD ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

