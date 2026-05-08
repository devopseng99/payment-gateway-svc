FROM docker.io/library/gradle:8.5-jdk21-alpine AS builder
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
RUN gradle dependencies --no-daemon --quiet 2>/dev/null || true
COPY src src
RUN gradle shadowJar --no-daemon --quiet

FROM docker.io/library/eclipse-temurin:21-jdk-alpine AS jlink
RUN jlink \
    --add-modules java.base,java.logging,java.xml,java.naming,java.management,java.security.sasl,java.security.jgss,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.unsupported,java.net.http,java.sql,java.desktop \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2 \
    --output /jre-minimal

FROM docker.io/library/alpine:3.19 AS runtime
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=jlink /jre-minimal /opt/jre
WORKDIR /app
COPY --from=builder /app/build/libs/payment-gateway-svc.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser
ENV PATH="/opt/jre/bin:$PATH"
ENV JAVA_HOME="/opt/jre"
EXPOSE 8000
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-jar", "app.jar"]
