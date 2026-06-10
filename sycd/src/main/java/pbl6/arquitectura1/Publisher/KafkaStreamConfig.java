package pbl6.arquitectura1.Publisher;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class KafkaStreamConfig {

    public static final String EXCHANGE_STREAM  = "stream_garraioa";  // cola tarea // del publisher al task worker // direct
    public static final String EXCHANGE_FANOUT  = "fanout_garraioa";  // a los workers
    public static final String EXCHANGE_EMAITZA = "emaitza_garraioa";  // result worker
    public static final String EXCHANGE_LAINOA  = "lainoa"; // a lainoa 1
    public static final String EXCHANGE_DLX     = "dlx_garraioa"; // cola dlq tarea //  para el taskworker // fanout

    public static final String QUEUE_TAREA   = "tarea";  // task worker
    public static final String QUEUE_KOTXEA  = "kotxea"; // worker  c
    public static final String QUEUE_PUBLIKO = "publikoa"; // worker p
    public static final String QUEUE_KP      = "k_p"; // tren bus
    public static final String QUEUE_EMAITZA = "emaitza"; // result worker
    public static final String QUEUE_DLQ     = "dlq_tarea"; // 

    private ConnectionFactory factory;

    public KafkaStreamConfig() {
        try {
            this.factory = pbl6.arquitectura1.Config.TLSConfig.crearFactory();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void configurar() {
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            // 1. Dead Letter Exchange + cola DLQ
            channel.exchangeDeclare(EXCHANGE_DLX, "direct", true);
            channel.queueDeclare(QUEUE_DLQ, true, false, false, null);
            channel.queueBind(QUEUE_DLQ, EXCHANGE_DLX, QUEUE_DLQ);
            System.out.println("[Config] DLX → dlq_tarea OK");

            // 2. STREAM (direct) → Q:tarea con DLX configurado
            channel.exchangeDeclare(EXCHANGE_STREAM, "direct", true);
            Map<String, Object> argsTarea = new HashMap<>();
            argsTarea.put("x-dead-letter-exchange", EXCHANGE_DLX);
            argsTarea.put("x-dead-letter-routing-key", QUEUE_DLQ);
            channel.queueDeclare(QUEUE_TAREA, true, false, false, argsTarea);
            channel.queueBind(QUEUE_TAREA, EXCHANGE_STREAM, QUEUE_TAREA);
            System.out.println("[Config] STREAM → tarea (con DLX) OK");

            // 3. FANOUT → kotxea / publikoa / k_p
            channel.exchangeDeclare(EXCHANGE_FANOUT, "fanout", true);
            channel.queueDeclare(QUEUE_KOTXEA,  true, false, false, null);
            channel.queueDeclare(QUEUE_PUBLIKO, true, false, false, null);
            channel.queueDeclare(QUEUE_KP,      true, false, false, null);
            channel.queueBind(QUEUE_KOTXEA,  EXCHANGE_FANOUT, "");
            channel.queueBind(QUEUE_PUBLIKO, EXCHANGE_FANOUT, "");
            channel.queueBind(QUEUE_KP,      EXCHANGE_FANOUT, "");
            System.out.println("[Config] FANOUT → kotxea/publikoa/k_p OK");

            // 4. EMAITZA (direct) → Q:emaitza
            channel.exchangeDeclare(EXCHANGE_EMAITZA, "direct", true);
            channel.queueDeclare(QUEUE_EMAITZA, true, false, false, null);
            channel.queueBind(QUEUE_EMAITZA, EXCHANGE_EMAITZA, QUEUE_EMAITZA);
            System.out.println("[Config] EMAITZA → emaitza OK");

            // 5. LAINOA (fanout)
            channel.exchangeDeclare(EXCHANGE_LAINOA, "fanout", true);
            System.out.println("[Config] LAINOA (fanout) OK");

            System.out.println("[Config] ✔ Configuración completada con DLX y TLS.");

        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new KafkaStreamConfig().configurar();
    }
}