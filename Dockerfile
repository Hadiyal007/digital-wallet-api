# ── Stage 1: Build ───────────────────────────────────────────
# Uses a full Maven + JDK image to compile the project.
# This stage's image is large (~500MB) but is discarded after build —
# none of it ends up in the final image.
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copy only the files needed to resolve dependencies first.
# Docker caches layers — if pom.xml hasn't changed, this layer is reused
# on rebuilds, meaning dependencies aren't re-downloaded every time you
# change a single Java file. This can save minutes per build.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Now copy the actual source code and build
COPY src src
RUN ./mvnw clean package -DskipTests -B

# ── Stage 2: Run ─────────────────────────────────────────────
# Uses a minimal JRE-only image (no compiler, no Maven) — much smaller
# (~200MB vs ~500MB) and a smaller attack surface in production.
FROM eclipse-temurin:21-jre AS run

WORKDIR /app

# Copy ONLY the built JAR from the build stage — nothing else.
COPY --from=build /app/target/digital-wallet.jar app.jar

# Render injects PORT at runtime; this EXPOSE is documentation only
# (doesn't actually open the port — that's handled by the platform).
EXPOSE 8080

# Run as a non-root user — standard security practice.
# Running containers as root is a common, avoidable vulnerability.
RUN useradd -m appuser
USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]