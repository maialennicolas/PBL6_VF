FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV DATA_DIR=/app/data
ENV SERVER_PORT=8083
ENV RABBITMQ_PORT=5671
ENV RABBITMQ_TLS_ENABLED=true
ENV RABBITMQ_TRUSTSTORE=/app/tls/truststore.jks
ENV RABBITMQ_TRUSTSTORE_PASSWORD=pbl6pass
COPY --from=build /app/target/*.jar /app/ecomove-web.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app/ecomove-web.jar"]
