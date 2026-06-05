package pbl6.arquitectura1.Publisher;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class KafkaStreamConfig {

    public static final String EXCHANGE_STREAM  = "stream_garraioa";
    public static final String EXCHANGE_FANOUT  = "fanout_garraioa";
    public static final String EXCHANGE_EMAITZA = "emaitza_garraioa";
    public static final String EXCHANGE_LAINOA  = "lainoa";
    public static final String EXCHANGE_DLX     = "dlx_garraioa";

    public static final String QUEUE_TAREA   = "tarea";
    public static final String QUEUE_KOTXEA  = "kotxea";
    public static final String QUEUE_PUBLIKO = "publikoa";
    public static final String QUEUE_KP      = "k_p";
    public static final String QUEUE_EMAITZA = "emaitza";

    public static final String QUEUE_DLQ          = "dlq_tarea";
    public static final String QUEUE_DLQ_KOTXEA   = "dlq_kotxea";
    public static final String QUEUE_DLQ_PUBLIKO  = "dlq_publikoa";
    public static final String QUEUE_DLQ_KP       = "dlq_k_p";
    public static final String QUEUE_DLQ_EMAITZA  = "dlq_emaitza";

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

            // 1. Dead Letter Exchange + DLQ colas de arquitectura 1.
            // Todas las colas críticas tienen DLX para que un basicNack(..., requeue=false)
            // no pierda mensajes y se puedan auditar/reprocesar errores.
            channel.exchangeDeclare(EXCHANGE_DLX, "direct", true);

            channel.exchangeDeclare(EXCHANGE_STREAM, "direct", true);
            declareQueueWithDlx(channel, QUEUE_TAREA, QUEUE_DLQ);
            channel.queueBind(QUEUE_TAREA, EXCHANGE_STREAM, QUEUE_TAREA);
            System.out.println("[Config] STREAM -> tarea (DLX/DLQ) OK");

            channel.exchangeDeclare(EXCHANGE_FANOUT, "fanout", true);
            declareQueueWithDlx(channel, QUEUE_KOTXEA, QUEUE_DLQ_KOTXEA);
            declareQueueWithDlx(channel, QUEUE_PUBLIKO, QUEUE_DLQ_PUBLIKO);
            declareQueueWithDlx(channel, QUEUE_KP, QUEUE_DLQ_KP);
            channel.queueBind(QUEUE_KOTXEA,  EXCHANGE_FANOUT, "");
            channel.queueBind(QUEUE_PUBLIKO, EXCHANGE_FANOUT, "");
            channel.queueBind(QUEUE_KP,      EXCHANGE_FANOUT, "");
            System.out.println("[Config] FANOUT -> kotxea/publikoa/k_p (DLX/DLQ) OK");

            channel.exchangeDeclare(EXCHANGE_EMAITZA, "direct", true);
            declareQueueWithDlx(channel, QUEUE_EMAITZA, QUEUE_DLQ_EMAITZA);
            channel.queueBind(QUEUE_EMAITZA, EXCHANGE_EMAITZA, QUEUE_EMAITZA);
            System.out.println("[Config] EMAITZA -> emaitza (DLX/DLQ) OK");

            channel.exchangeDeclare(EXCHANGE_LAINOA, "fanout", true);
            System.out.println("[Config] LAINOA (fanout) OK");

            System.out.println("[Config] Configuracion completada con TLS, ACK manual y DLX/DLQ.");

        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    public static void declareQueueWithDlx(Channel channel, String queueName, String dlqName) throws IOException {
        declareDlq(channel, dlqName);
        channel.queueDeclare(queueName, true, false, false, dlxArgs(dlqName));
    }

    public static void declareDlq(Channel channel, String dlqName) throws IOException {
        channel.exchangeDeclare(EXCHANGE_DLX, "direct", true);
        channel.queueDeclare(dlqName, true, false, false, null);
        channel.queueBind(dlqName, EXCHANGE_DLX, dlqName);
    }

    public static Map<String, Object> dlxArgs(String dlqRoutingKey) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", EXCHANGE_DLX);
        args.put("x-dead-letter-routing-key", dlqRoutingKey);
        return args;
    }

    public static AMQP.BasicProperties persistentTextProperties() {
        return new AMQP.BasicProperties.Builder()
                .deliveryMode(2)
                .contentType("text/plain")
                .build();
    }

    public static void main(String[] args) {
        new KafkaStreamConfig().configurar();
    }
}
