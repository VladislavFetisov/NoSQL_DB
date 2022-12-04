FROM gradle:7.5.1-jdk17-alpine AS build
COPY --chown=gradle:gradle . .
ENTRYPOINT ./gradlew run --stacktrace