package pbl6.arquitectura1.Config;

import com.rabbitmq.client.ConnectionFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;

public class TLSConfig {

    private static final String HOST = env("RABBITMQ_HOST", "localhost");
    private static final boolean TLS_ENABLED = Boolean.parseBoolean(env("RABBITMQ_TLS_ENABLED", "false"));
    private static final int PORT = Integer.parseInt(env("RABBITMQ_PORT", TLS_ENABLED ? "5671" : "5672"));
    private static final String USER = env("RABBITMQ_USER", "guest");
    private static final String PASS = env("RABBITMQ_PASS", "guest");
    private static final String TRUSTSTORE_PATH = env("RABBITMQ_TRUSTSTORE", "/app/tls/truststore.jks");
    private static final String TRUSTSTORE_PASSWORD = env("RABBITMQ_TRUSTSTORE_PASSWORD", "pbl6pass");

    public static ConnectionFactory crearFactory() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        factory.setPort(PORT);
        factory.setUsername(USER);
        factory.setPassword(PASS);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(true);

        if (TLS_ENABLED) {
            factory.useSslProtocol(createSslContext());
            factory.enableHostnameVerification();
            System.out.println("[RabbitMQ][TLS] Conectando a amqps://" + HOST + ":" + PORT + " como " + USER
                    + " | truststore=" + TRUSTSTORE_PATH);
        } else {
            System.out.println("[RabbitMQ][AMQP] Conectando a amqp://" + HOST + ":" + PORT + " como " + USER);
        }

        return factory;
    }

    private static SSLContext createSslContext() throws Exception {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream in = new FileInputStream(TRUSTSTORE_PATH)) {
            trustStore.load(in, TRUSTSTORE_PASSWORD.toCharArray());
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
        sslContext.init(null, tmf.getTrustManagers(), null);
        return sslContext;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
