# syntax=docker/dockerfile:1.7
# Single image for notifications-api and notifications-worker.
# The role is chosen at runtime with SPRING_PROFILES_ACTIVE=api|worker (see compose.yaml).
# Build context is the repository root: docker build -f docker/notifications.Dockerfile .

# ---------------------------------------------------------------- build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 1. POMs and wrapper first so the dependency layer is cached until a POM changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY domain/pom.xml domain/
COPY application/pom.xml application/
COPY infrastructure/pom.xml infrastructure/
COPY event-simulator/pom.xml event-simulator/
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -q -B -pl infrastructure -am dependency:go-offline

# 2. Sources; only the modules the service needs.
COPY domain domain
COPY application application
COPY infrastructure infrastructure
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -q -B -pl infrastructure -am -DskipTests package

# 3. Explode the boot jar into layers (dependencies change far less often than application code).
RUN java -Djarmode=tools -jar infrastructure/target/*.jar extract --layers --destination /workspace/extracted

# -------------------------------------------------------------- runtime
FROM eclipse-temurin:21-jre
# wget is required by the Compose healthcheck (/actuator/health/readiness).
RUN apt-get update \
 && apt-get install -y --no-install-recommends wget \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --create-home app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./
USER app
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
