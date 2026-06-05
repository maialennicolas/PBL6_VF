package com.ecomove.service;

import com.ecomove.model.LocationTrackRequest;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Publica eventos reales WEB -> SYCD en RabbitMQ.
 *
 * Formato enviado a Q:tarea:
 * userId empresaId lat lon velocidadKmh metrosAcumulados terminado sessionId timestampMs
 */
@Service
public class RabbitMqTripPublisher {

    private static final String EXCHANGE_STREAM = "stream_garraioa";
    private static final String EXCHANGE_DLX = "dlx_garraioa";
    private static final String QUEUE_TAREA = "tarea";
    private static final String QUEUE_DLQ = "dlq_tarea";

    private final ConnectionFactory factory;

    public RabbitMqTripPublisher() {
        this.factory = new ConnectionFactory();
        boolean tlsEnabled = Boolean.parseBoolean(env("RABBITMQ_TLS_ENABLED", "true"));
        factory.setHost(env("RABBITMQ_HOST", "localhost"));
        factory.setPort(Integer.parseInt(env("RABBITMQ_PORT", tlsEnabled ? "5671" : "5672")));
        factory.setUsername(env("RABBITMQ_USER", "guest"));
        factory.setPassword(env("RABBITMQ_PASS", "guest"));
        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(true);

        if (tlsEnabled) {
            try {
                factory.useSslProtocol(createSslContext());
                factory.enableHostnameVerification();
                System.out.println("[WEB->SYCD][TLS] RabbitMQ amqps://"
                        + env("RABBITMQ_HOST", "localhost") + ":" + env("RABBITMQ_PORT", "5671"));
            } catch (Exception e) {
                throw new IllegalStateException("No se ha podido activar TLS para RabbitMQ: " + e.getMessage(), e);
            }
        } else {
            System.out.println("[WEB->SYCD][AMQP] RabbitMQ amqp://"
                    + env("RABBITMQ_HOST", "localhost") + ":" + env("RABBITMQ_PORT", "5672"));
        }
    }

    public boolean publish(LocationTrackRequest request,
                           long empresaId,
                           double meters,
                           boolean finished,
                           String eventTimestamp) {
        if (request == null || request.sessionId() == null || request.sessionId().isBlank()) {
            return false;
        }

        double speedKmh = toKmh(request.speed());
        long timestampMs = toEpochMillis(eventTimestamp == null || eventTimestamp.isBlank()
                ? request.timestamp()
                : eventTimestamp);

        String message = String.format(Locale.US,
                "%d %d %.6f %.6f %.2f %.2f %b %s %d",
                request.userId(),
                empresaId,
                request.latitude(),
                request.longitude(),
                speedKmh,
                Math.max(0.0, meters),
                finished,
                request.sessionId(),
                timestampMs);

        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
            ensureTopology(channel);
            channel.basicPublish(
                    EXCHANGE_STREAM,
                    QUEUE_TAREA,
                    persistentTextProperties(),
                    message.getBytes(StandardCharsets.UTF_8));

            System.out.println("[WEB->SYCD] Evento enviado: " + message);
            return true;
        } catch (Exception e) {
            System.err.println("[WEB->SYCD] No se ha podido enviar evento a SYCD: " + e.getMessage());
            return false;
        }
    }

    private void ensureTopology(Channel channel) throws Exception {
        channel.exchangeDeclare(EXCHANGE_DLX, "direct", true);
        channel.queueDeclare(QUEUE_DLQ, true, false, false, null);
        channel.queueBind(QUEUE_DLQ, EXCHANGE_DLX, QUEUE_DLQ);

        channel.exchangeDeclare(EXCHANGE_STREAM, "direct", true);
        Map<String, Object> argsTarea = new HashMap<>();
        argsTarea.put("x-dead-letter-exchange", EXCHANGE_DLX);
        argsTarea.put("x-dead-letter-routing-key", QUEUE_DLQ);
        channel.queueDeclare(QUEUE_TAREA, true, false, false, argsTarea);
        channel.queueBind(QUEUE_TAREA, EXCHANGE_STREAM, QUEUE_TAREA);
    }

    /**
     * El navegador normalmente manda coords.speed en m/s. SYCD trabaja mejor en km/h.
     */
    private double toKmh(Double browserSpeed) {
        if (browserSpeed == null || browserSpeed <= 0.0 || browserSpeed.isNaN() || browserSpeed.isInfinite()) {
            return 0.0;
        }
        return browserSpeed * 3.6;
    }

    private long toEpochMillis(String timestamp) {
        try {
            if (timestamp == null || timestamp.isBlank()) return System.currentTimeMillis();
            return Instant.parse(timestamp).toEpochMilli();
        } catch (Exception ignored) {
            return System.currentTimeMillis();
        }
    }


    private com.rabbitmq.client.AMQP.BasicProperties persistentTextProperties() {
        return new com.rabbitmq.client.AMQP.BasicProperties.Builder()
                .deliveryMode(2)
                .contentType("text/plain")
                .build();
    }

    private SSLContext createSslContext() throws Exception {
        String truststorePath = env("RABBITMQ_TRUSTSTORE", "/app/tls/truststore.jks");
        String truststorePassword = env("RABBITMQ_TRUSTSTORE_PASSWORD", "pbl6pass");

        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream in = new FileInputStream(truststorePath)) {
            trustStore.load(in, truststorePassword.toCharArray());
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(null, tmf.getTrustManagers(), null);
        return sslContext;
    }

    private String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
