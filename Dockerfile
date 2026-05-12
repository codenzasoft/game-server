FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src
COPY ui ui
RUN ./gradlew bootJar --no-daemon -PviteMode=production

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/game-server-0.0.1-SNAPSHOT.jar app.jar
COPY --from=build /app/build/newrelic/newrelic.jar /newrelic/newrelic.jar
EXPOSE 8080
ENTRYPOINT ["java", "-javaagent:/newrelic/newrelic.jar", "-jar", "app.jar"]
