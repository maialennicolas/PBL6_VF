FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:copy-dependencies
COPY src ./src
RUN mvn -q -DskipTests package dependency:copy-dependencies

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV DATA_DIR=/app/data
ENV RABBITMQ_HOST=rabbitmq
ENV RABBITMQ_PORT=5671
ENV RABBITMQ_TLS_ENABLED=true
ENV RABBITMQ_TRUSTSTORE=/app/tls/truststore.jks
ENV RABBITMQ_TRUSTSTORE_PASSWORD=pbl6pass
COPY --from=build /app/target /app/target
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN sed -i 's/\r$//' /app/docker-entrypoint.sh && chmod +x /app/docker-entrypoint.sh
ENTRYPOINT ["/app/docker-entrypoint.sh"]
