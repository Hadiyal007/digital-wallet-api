# ── Stage 1: Build ────────────────────────────────────────────
# Full JDK + Maven image to compile. This stage is discarded after
# build — it never ends up in the final deployed image.
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copy dependency files first — Docker layer caching means
# dependencies are only re-downloaded when pom.xml changes,
# not every time you change a Java file. Saves minutes per build.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Now copy source and build
COPY src src
RUN ./mvnw clean package -DskipTests -B

# ── Stage 2: Run ──────────────────────────────────────────────
# Minimal JRE-only image — no compiler, no Maven, smaller attack surface.
# ~200MB vs ~500MB for the build image.
FROM eclipse-temurin:21-jre AS run

WORKDIR /app

# Copy ONLY the compiled JAR from Stage 1
COPY --from=build /app/target/digital-wallet.jar app.jar

EXPOSE 8080

# Run as non-root user — standard container security practice
RUN useradd -m appuser
USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]