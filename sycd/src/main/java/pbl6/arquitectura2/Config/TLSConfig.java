package pbl6.arquitectura2.Config;

import com.rabbitmq.client.ConnectionFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.HashSet;
import java.util.Arrays;

public class TLSConfig {

    // Se añade "demo/" al inicio porque VS Code ejecuta los comandos desde la raíz del espacio de trabajo
    private static final String TRUSTSTORE_PATH = "demo/tls/truststore.jks";
    private static final String KEYSTORE_PATH   = "demo/tls/keystore.jks";
    private static final String PASSWORD        = "pbl6pass";

    private static final String HOST = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
    private static final int    PORT = Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672"));
    private static final String USER = System.getenv().getOrDefault("RABBITMQ_USER", "guest");
    private static final String PASS = System.getenv().getOrDefault("RABBITMQ_PASS", "guest");

    public static ConnectionFactory crearFactory() throws Exception {
        // Ignoramos temporalmente los ficheros JKS locales para que no interfieran en el puerto normal
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        factory.setPort(PORT); // <--- CAMBIO CLAVE: Usamos el puerto estándar abierto en tu Docker
        factory.setUsername(USER);
        factory.setPassword(PASS);

        // Imprimimos un mensaje claro en consola para saber que estamos saltándonos el TLS en local
        System.out.println("[RabbitMQ] Conectando a " + HOST + ":" + PORT + " como " + USER);
        return factory;
    }
}