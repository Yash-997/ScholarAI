# Stage 1: Build the Java application
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and build production jar
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Create non-root user for security
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

# Copy built artifact from build stage
COPY --from=build /app/target/scholarship-backend-1.0.0.jar app.jar

# Expose Spring Boot web port
EXPOSE 8080

# Configure JVM flags and launch application
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
