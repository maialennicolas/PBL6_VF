package pbl6.arquitectura1.Config;

import com.rabbitmq.client.ConnectionFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;

public class TLSConfig {

    private static final String TRUSTSTORE_PATH = "demo/tls/truststore.jks";
    private static final String KEYSTORE_PATH   = "demo/tls/keystore.jks";
    private static final String PASSWORD        = "pbl6pass";

    private static final String HOST = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
    private static final int    PORT = Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672"));
    private static final String USER = System.getenv().getOrDefault("RABBITMQ_USER", "guest");
    private static final String PASS = System.getenv().getOrDefault("RABBITMQ_PASS", "guest");

    public static ConnectionFactory crearFactory() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        factory.setPort(PORT);
        factory.setUsername(USER);
        factory.setPassword(PASS);
        System.out.println("[RabbitMQ] Conectando a " + HOST + ":" + PORT + " como " + USER);
        return factory;
    }
}