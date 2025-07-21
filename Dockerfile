# ===== Stage 1: Build =====
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

COPY mvnw .
COPY mvnw.cmd .
COPY .mvn/ .mvn/

RUN chmod +x mvnw

COPY pom.xml .

RUN ./mvnw dependency:go-offline -B

COPY src ./src

RUN ./mvnw package -DskipTests

# ===== Development Stage =====
FROM build AS development

RUN apt-get update && apt-get install -y --no-install-recommends \
    procps \
    vim \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

ENV JAVA_OPTS="-Xmx512m -Xms256m \
    -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
    -Dspring.profiles.active=dev"

EXPOSE 8080 5005

ENTRYPOINT ["sh", "-c", "./mvnw spring-boot:run -Dspring-boot.run.jvmArguments=\"$JAVA_OPTS\""]

# ===== Production Stage =====
FROM eclipse-temurin:21-jre-alpine AS final

RUN apk update && \
    apk upgrade && \
    apk add --no-cache \
        wget \
        curl \
        tzdata \
        ca-certificates && \
    rm -rf /var/cache/apk/*

RUN addgroup -g 1001 -S spiketracker && \
    adduser -S spiketracker -u 1001 -G spiketracker -h /app -s /sbin/nologin

WORKDIR /app

COPY --from=build --chown=spiketracker:spiketracker /app/target/spike-tracker-*.jar ./app.jar

RUN mkdir -p logs && chown -R spiketracker:spiketracker logs

RUN find /app -name "*.sh" -exec chmod -x {} \; 2>/dev/null || true

USER spiketracker

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider --timeout=5 http://localhost:8080/api/health || exit 1

EXPOSE 8080

ENV JAVA_OPTS="-Xmx512m -Xms256m \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+PrintGCDetails \
    -XX:+PrintGCTimeStamps \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.profiles.active=prod \
    -Dlogging.level.org.springframework.security=WARN"

LABEL maintainer="GekkoQuest" \
      version="2.1.0" \
      description="SpikeTracker - Valorant Esports Tracker" \
      org.opencontainers.image.source="https://github.com/GekkoQuest/spike-tracker" \
      org.opencontainers.image.vendor="GekkoQuest" \
      org.opencontainers.image.title="SpikeTracker" \
      org.opencontainers.image.description="Real-time Valorant match tracking application"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]