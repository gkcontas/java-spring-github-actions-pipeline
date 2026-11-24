# The jar is built (and tested) by Gradle before this file runs: in CI the build job
# already has a warm Gradle cache and a Docker daemon for Testcontainers. Compiling
# again inside a builder stage would redo that work with a cold cache.

FROM eclipse-temurin:21-jre-alpine AS extract
WORKDIR /build
COPY build/libs/*.jar app.jar
# Split the fat jar into layers ordered by how often they change. Dependencies are
# ~95% of the bytes and change on a version bump; the application layer changes on
# every commit and is a few dozen KB.
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=extract /build/extracted/dependencies/ ./
COPY --from=extract /build/extracted/spring-boot-loader/ ./
COPY --from=extract /build/extracted/snapshot-dependencies/ ./
COPY --from=extract /build/extracted/application/ ./
USER app
EXPOSE 8080
# Heap sized from the container limit, not from the host's RAM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
