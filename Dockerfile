FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace
ARG SERVICE

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle ./
COPY common common
COPY service service

RUN chmod +x gradlew \
    && ./gradlew ":service:${SERVICE}:bootJar" --no-daemon -x test \
    && cp "service/${SERVICE}/build/libs/"*.jar /workspace/app.jar

# Snowflake uses the JDK random-generator provider (jdk.random), which is not
# included in every slim JRE distribution. Keep the full Java 21 runtime so
# container behavior matches the development/test JVM.
FROM eclipse-temurin:21-jdk

WORKDIR /app
COPY --from=builder /workspace/app.jar app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
