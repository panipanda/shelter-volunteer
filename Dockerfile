FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
# The tests read the real content files, so the build stage needs them too.
COPY data ./data
COPY content ./content
# A failing test fails the build, so a broken commit never reaches Render.
RUN chmod +x gradlew && ./gradlew --no-daemon test installDist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/install/shelter-volunteer ./
COPY data ./data
COPY content ./content

# The app only reads its files, so it does not need root.
RUN useradd --system --no-create-home --shell /usr/sbin/nologin app
USER app

EXPOSE 8080
ENTRYPOINT ["./bin/shelter-volunteer"]
