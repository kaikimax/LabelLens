# The LabelLens app as one container image.
#   docker compose up --build        (database + app, see compose.yaml)
#   docker build -t labellens .      (image only)
#
# Two stages: Maven and the JDK are only needed to BUILD the jar; the image that runs it only
# has a Java runtime (smaller, fewer things that can have security holes).

# ---- 1. Build ------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# The poms first, then the code: Docker reuses this layer while only code changes.
COPY pom.xml .
COPY eval-core/pom.xml eval-core/
COPY app/pom.xml app/
COPY eval-core/src eval-core/src
COPY app/src app/src

# -am also builds eval-core (the app depends on it). maven.test.skip: the tests run in CI and
# need Docker themselves (Testcontainers); they don't belong in an image build.
# No BuildKit cache mount: Azure's "az acr build" must be able to build this file too.
RUN mvn -B -q -pl app -am package -Dmaven.test.skip=true

# ---- 2. Run --------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre

# Don't run as root: if the app is ever broken into, the attacker isn't root in the container.
RUN useradd --system --uid 10001 --no-create-home labellens \
    && mkdir -p /app/uploads && chown labellens /app/uploads
WORKDIR /app
COPY --from=build /src/app/target/labellens-app-*.jar app.jar
USER labellens

# Uploaded photos. Locally a Docker volume; on Azure, Blob Storage later (SPEC).
ENV PHOTO_DIR=/app/uploads
EXPOSE 8080

# MaxRAMPercentage: use up to 75% of the container's memory limit, not of the whole machine.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]