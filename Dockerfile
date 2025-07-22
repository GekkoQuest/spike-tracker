# ===== Stage 1: Build =====
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

COPY . .
RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

# ===== Stage 2: Runtime =====
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /app/target/spike-tracker-*.jar app.jar

COPY --from=builder /app/src/main/resources/application*.properties /app/

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD wget -q --spider http://localhost:8080/api/health || exit 1

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["sh", "-c", "exec java -Dspring.profiles.active=$SPRING_PROFILES_ACTIVE -Dserver.port=$PORT -jar app.jar"]