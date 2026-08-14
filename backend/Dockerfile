FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy

RUN useradd --system --uid 10001 alzswell
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar
USER alzswell

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
