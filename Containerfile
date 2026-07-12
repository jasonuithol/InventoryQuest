# ── Stage 1: build the Boot jar ────────────────────────────────────────────────
FROM docker.io/library/maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependencies first: only re-resolves when pom.xml changes.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# Build the application jar (tests run in CI via `mvn verify`, not in the image build).
COPY src ./src
RUN mvn -q -B -DskipTests clean package

# Split the fat jar into an app jar + a lib/ directory so dependencies form their own
# image layer and only re-ship when they actually change.
RUN cp target/inventoryquest-*.jar app.jar && \
    java -Djarmode=tools -jar app.jar extract --destination extracted

# ── Stage 2: slim runtime ──────────────────────────────────────────────────────
FROM docker.io/library/eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Dependencies as their own layer (changes rarely), then the application (changes often).
COPY --from=build /build/extracted/lib/ ./lib/
COPY --from=build /build/extracted/app.jar ./app.jar

# Virtual threads carry every connection; plain blocking code inside.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
