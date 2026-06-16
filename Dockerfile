# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the executable jar with the Gradle wrapper ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Copy build scripts first so dependency resolution is cached across source changes.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon help

# Now copy sources and build the boot jar (tests are run separately in CI).
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- Runtime stage: minimal JRE image running as a non-root user ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# Run as an unprivileged user.
RUN useradd --system --uid 10001 --shell /usr/sbin/nologin appuser
USER 10001

COPY --from=build /workspace/build/libs/app.jar /app/app.jar

EXPOSE 8080

# Container-aware JVM defaults; override JAVA_OPTS at deploy time if needed.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
