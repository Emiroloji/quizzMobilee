# Jar ve OTel agent'ı CI'da Gradle üretir (bootJar + otelAgentJar); imaj yalnızca çalışma ortamıdır.
FROM eclipse-temurin:25.0.4.1_1-jre-noble

RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
COPY --chown=app:app build/libs/soruhavuzu-api.jar app.jar
COPY --chown=app:app build/otel/opentelemetry-javaagent.jar otel/opentelemetry-javaagent.jar
USER app

ENV JAVA_TOOL_OPTIONS="-javaagent:/app/otel/opentelemetry-javaagent.jar -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    OTEL_SERVICE_NAME=soruhavuzu-api \
    OTEL_TRACES_EXPORTER=none \
    OTEL_METRICS_EXPORTER=none \
    OTEL_LOGS_EXPORTER=none
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
