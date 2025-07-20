FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY mvnw.cmd .

RUN ./mvnw dependency:go-offline -B && ./mvnw package -DskipTests

COPY src ./src

FROM eclipse-temurin:21-jre-alpine AS production

RUN addgroup -g 1001 -S spiketracker && \
    adduser -S spiketracker -u 1001 -G spiketracker

WORKDIR /app

COPY --from=build /app/target/spike-tracker-*.jar ./app.jar

RUN chown -R spiketracker:spiketracker /app

USER spiketracker

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/health || exit 1

EXPOSE 8080

ENV JAVA_OPTS="-Xmx512m -Xms256m" \
    SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]