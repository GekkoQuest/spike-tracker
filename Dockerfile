FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

ARG DISCORD_TOKEN

COPY pom.xml .
COPY src ./src

RUN mvn clean install -Ddiscord.token=$DISCORD_TOKEN

FROM openjdk:21-jdk-slim
WORKDIR /app

COPY --from=build /app/target/spike-tracker-*.jar ./app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]