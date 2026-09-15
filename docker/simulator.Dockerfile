# syntax=docker/dockerfile:1.7
# event-simulator: test double of the emitting platform. Independent from domain/application.
# Build context is the repository root: docker build -f docker/simulator.Dockerfile .

# ---------------------------------------------------------------- build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY domain/pom.xml domain/
COPY application/pom.xml application/
COPY infrastructure/pom.xml infrastructure/
COPY event-simulator/pom.xml event-simulator/
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -q -B -pl event-simulator dependency:go-offline

COPY event-simulator event-simulator
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -q -B -pl event-simulator -DskipTests package

RUN java -Djarmode=tools -jar event-simulator/target/*.jar extract --layers --destination /workspace/extracted

# -------------------------------------------------------------- runtime
FROM eclipse-temurin:21-jre
# wget is required by the Compose healthcheck (/actuator/health).
RUN apt-get update \
 && apt-get install -y --no-install-recommends wget \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --create-home app
WORKDIR /app
# Reference data set: the simulator emits these events on start and keeps deriving new ones.
COPY --chown=app:app docs/notification_events.json /app/data/notification_events.json
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./
USER app
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
