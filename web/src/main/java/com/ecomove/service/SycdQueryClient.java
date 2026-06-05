package com.ecomove.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Cliente RPC Web -> SYCD.
 * La web expone REST, pero la consulta real se resuelve en SYCD mediante RabbitMQ/TLS.
 */
@Service
public class SycdQueryClient {

    private static final String REQUEST_QUEUE = "q.co2.consultas";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final ConnectionFactory factory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SycdQueryClient() {
        this.factory = new ConnectionFactory();
        boolean tlsEnabled = Boolean.parseBoolean(env("RABBITMQ_TLS_ENABLED", "true"));
        factory.setHost(env("RABBITMQ_HOST", "localhost"));
        factory.setPort(Integer.parseInt(env("RABBITMQ_PORT", tlsEnabled ? "5671" : "5672")));
        factory.setUsername(env("RABBITMQ_USER", "guest"));
        factory.setPassword(env("RABBITMQ_PASS", "guest"));
        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(true);
        factory.setRequestedHeartbeat(30);
        factory.setConnectionTimeout(5000);

        if (tlsEnabled) {
            try {
                factory.useSslProtocol(createSslContext());
                factory.enableHostnameVerification();
                System.out.println("[WEB->SYCD-RPC][TLS] Consultas SYCD por amqps://"
                        + env("RABBITMQ_HOST", "localhost") + ":" + env("RABBITMQ_PORT", "5671"));
            } catch (Exception e) {
                throw new IllegalStateException("No se ha podido activar TLS para consultas SYCD: " + e.getMessage(), e);
            }
        } else {
            System.out.println("[WEB->SYCD-RPC][AMQP] Consultas SYCD por amqp://"
                    + env("RABBITMQ_HOST", "localhost") + ":" + env("RABBITMQ_PORT", "5672"));
        }
    }

    public Map<String, Object> status() {
        return request("STATUS");
    }

    public Map<String, Object> metrics() {
        return request("METRICS");
    }

    public Map<String, Object> userCo2(long userId) {
        return request("USER " + userId);
    }

    public Map<String, Object> companyCo2(long empresaId) {
        return request("EMPRESA " + empresaId);
    }

    public Map<String, Object> companyUserCo2(long empresaId, long userId) {
        return request("EMPRESA_USER " + empresaId + ":" + userId);
    }

    public Map<String, Object> request(String message) {
        long start = System.nanoTime();
        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
            channel.queueDeclare(REQUEST_QUEUE, true, false, false, null);

            String corrId = UUID.randomUUID().toString();
            String replyQueue = channel.queueDeclare("", false, true, true, null).getQueue();
            BlockingQueue<String> response = new ArrayBlockingQueue<>(1);

            String consumerTag = channel.basicConsume(replyQueue, true, (tag, delivery) -> {
                String responseCorrId = delivery.getProperties().getCorrelationId();
                if (corrId.equals(responseCorrId)) {
                    response.offer(new String(delivery.getBody(), StandardCharsets.UTF_8));
                }
            }, tag -> { });

            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .correlationId(corrId)
                    .replyTo(replyQueue)
                    .contentType("text/plain")
                    .deliveryMode(1)
                    .build();

            channel.basicPublish("", REQUEST_QUEUE, props, message.getBytes(StandardCharsets.UTF_8));

            String payload = response.poll(DEFAULT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            channel.basicCancel(consumerTag);

            if (payload == null) {
                return error("Timeout esperando respuesta de SYCD para: " + message, start);
            }

            Map<String, Object> result = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
            result.put("rpcLatencyMs", elapsedMs(start));
            return result;
        } catch (Exception e) {
            return error("No se ha podido consultar SYCD: " + e.getMessage(), start);
        }
    }

    private Map<String, Object> error(String message, long start) {
        Map<String, Object> result = new HashMap<>();
        result.put("ok", false);
        result.put("error", message);
        result.put("rpcLatencyMs", elapsedMs(start));
        return result;
    }

    private long elapsedMs(long startNano) {
        return Math.max(0, (System.nanoTime() - startNano) / 1_000_000L);
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
