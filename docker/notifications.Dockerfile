# cobre/notifications — API and worker in one image; the role is chosen at runtime
# with SPRING_PROFILES_ACTIVE=api|worker (see docs/01-system-design.html, section 12).
#
# NOTE: this Dockerfile was authored before any Java source exists in the repository,
# so it has not been built yet. The Maven commands below were validated locally against
# the POM-only build (`./mvnw -q -pl infrastructure -am dependency:go-offline` and
# `./mvnw -q -DskipTests verify`), and the layer extraction layout was checked with the
# resulting jar. Build from the repository root:
#   docker build -f docker/notifications.Dockerfile -t cobre/notifications .

# ---------- Stage 1: build ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# 1) POMs + wrapper first: this layer only changes when dependencies change,
#    so the dependency download below stays cached across source edits.
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY domain/pom.xml domain/pom.xml
COPY application/pom.xml application/pom.xml
COPY infrastructure/pom.xml infrastructure/pom.xml
COPY event-simulator/pom.xml event-simulator/pom.xml
RUN chmod +x mvnw \
 && ./mvnw -q -B -pl infrastructure -am dependency:go-offline

# 2) Sources of the hexagon and its Spring Boot adapters. The simulator is not needed here.
COPY domain/src domain/src
COPY application/src application/src
COPY infrastructure/src infrastructure/src
RUN ./mvnw -q -B -pl infrastructure -am package -DskipTests

# 3) Split the fat jar into layers (dependencies / snapshot-dependencies / application)
#    so a one-line Java change does not invalidate the dependency layer of the final image.
RUN cp infrastructure/target/notifications.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine

# Non-root user. Alpine ships wget via busybox (/usr/bin/wget), which Compose healthchecks use.
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app

# Copy layers in order of least to most likely to change.
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./

USER app
EXPOSE 8080

# The extracted app.jar carries the Main-Class and a Class-Path pointing at ./lib,
# so it launches directly without the nested-jar loader.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
