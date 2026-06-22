# ── Build Stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# 의존성 레이어 캐싱: 소스 변경 시 이 레이어는 재사용
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle .
COPY settings.gradle .

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q

# 소스 복사 후 빌드
COPY src/ src/

RUN ./gradlew bootJar -x test --no-daemon && \
    cp build/libs/*.jar app.jar


# ── Run Stage ────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre AS runner

WORKDIR /app

COPY --from=builder /app/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
