# cobre/event-simulator — test double for the emitting platform. It publishes the reference
# events to the SQS-compatible queue and deliberately does not depend on domain or
# application (see docs/01-system-design.html, section 12).
#
# NOTE: this Dockerfile was authored before any Java source exists in the repository,
# so it has not been built yet. The Maven commands below were validated locally against
# the POM-only build (`./mvnw -q -DskipTests verify`), and the layer extraction layout was
# checked with the resulting jar. Build from the repository root:
#   docker build -f docker/simulator.Dockerfile -t cobre/event-simulator .

# ---------- Stage 1: build ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# 1) POMs + wrapper first, so the dependency download is cached across source edits.
#    The root POM lists every module, so all module POMs must be present for the reactor to load.
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY domain/pom.xml domain/pom.xml
COPY application/pom.xml application/pom.xml
COPY infrastructure/pom.xml infrastructure/pom.xml
COPY event-simulator/pom.xml event-simulator/pom.xml
RUN chmod +x mvnw \
 && ./mvnw -q -B -pl event-simulator -am dependency:go-offline

# 2) Only the simulator sources: it has no upstream module.
COPY event-simulator/src event-simulator/src
RUN ./mvnw -q -B -pl event-simulator -am package -DskipTests

# 3) Split the fat jar into layers so dependency layers are reused across code changes.
RUN cp event-simulator/target/event-simulator.jar app.jar \
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
