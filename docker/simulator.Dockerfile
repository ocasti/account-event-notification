# cobre/event-simulator — test double for the emitting platform. It publishes the reference
# events to the SQS-compatible queue and deliberately does not depend on domain or
# application (see docs/01-system-design.html, section 12).
#
# NOTE: this Dockerfile was authored before any Java source exists in the repository,
# so it has not been built yet. The Maven commands below were validated locally against
# the POM-only build (`./mvnw -q -DskipTests verify`), and the layer extraction layout was
# checked with the resulting jar. The Maven project lives in services/event-simulator (a
# standalone project, not a module of the notifications reactor); the build context stays
# the repository root so .dockerignore applies. Build from the repository root:
#   docker build -f docker/simulator.Dockerfile -t cobre/event-simulator .

# ---------- Stage 1: build ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace/services/event-simulator

# 1) POM + wrapper first, so the dependency download is cached across source edits.
#    Single-module project: no other POMs are needed.
COPY services/event-simulator/mvnw services/event-simulator/pom.xml ./
COPY services/event-simulator/.mvn .mvn
RUN chmod +x mvnw \
 && ./mvnw -q -B dependency:go-offline

# 2) Only the simulator sources: it has no upstream module.
COPY services/event-simulator/src src
RUN ./mvnw -q -B package -DskipTests

# 3) Split the fat jar into layers so dependency layers are reused across code changes.
RUN cp target/event-simulator.jar /workspace/app.jar \
 && cd /workspace \
 && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine

# Non-root user. Alpine ships wget via busybox (/usr/bin/wget), which Compose healthchecks use.
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app

COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./

USER app
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
