FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
RUN ./gradlew --no-daemon installDist -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/install/shelter-volunteer ./
COPY data ./data
COPY content ./content

EXPOSE 8080
ENTRYPOINT ["./bin/shelter-volunteer"]
