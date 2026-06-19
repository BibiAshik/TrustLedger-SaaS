# =============================================================================
# Dockerfile — Multi-stage build for Trust Ledger SaaS
# =============================================================================
# Stage 1: Build the application with Maven
# Stage 2: Run the application with a slim JDK image
# =============================================================================

# ----- STAGE 1: BUILD -----
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy the Maven project files first (for Docker layer caching).
# If pom.xml doesn't change, Maven won't re-download dependencies.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the source code and build the application
COPY src/ src/
RUN mvn clean package -DskipTests -B

# ----- STAGE 2: RUN -----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create directories for file uploads and logs
RUN mkdir -p /app/uploads /app/logs

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose the default Spring Boot port
EXPOSE 8080

# Environment variable defaults (can be overridden in docker-compose)
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xms256m -Xmx512m"

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/auth/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
