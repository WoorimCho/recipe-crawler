# ── build ───────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
RUN apk add --no-cache maven
COPY pom.xml ./
RUN mvn -B dependency:go-offline || true
COPY src ./src
RUN mvn -B clean package -DskipTests

# ── runtime ─────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
