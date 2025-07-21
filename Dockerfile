# ===== Stage 1 =====
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

COPY . .

RUN chmod +x mvnw

RUN ./mvnw clean package spring-boot:repackage -DskipTests && \
    unzip -l target/spike-tracker-2.1.0-SNAPSHOT.jar | grep postgresql || true

# ===== Stage 2: Runtime =====
FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

COPY --from=builder /app/target/spike-tracker-2.1.0-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "echo DATABASE_URL=$DATABASE_URL && java -Dserver.port=${PORT:-8080} -Dserver.address=0.0.0.0 -jar app.jar"]