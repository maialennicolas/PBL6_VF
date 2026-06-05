package pbl6.arquitectura2.Config;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CO2StreamConfig {

    public static final String EXCHANGE_CO2_FANOUT    = "fanout_co2";
    public static final String QUEUE_CO2              = "co2";
    public static final String EXCHANGE_CO2_RESULTADO = "resultado_co2";
    public static final String QUEUE_CO2_RESULTADO    = "resultado_co2";
    public static final String EXCHANGE_LAINOA2       = "lainoa2";
    public static final String QUEUE_CO2_CONSULTAS    = "q.co2.consultas";
    public static final String QUEUE_LAINOA2_AUDITORIA = "q.lainoa2.auditoria";

    public static final String EXCHANGE_DLX_CO2       = "dlx_co2";
    public static final String QUEUE_DLQ_CO2          = "dlq_co2";
    public static final String QUEUE_DLQ_CO2_RESULTADO = "dlq_resultado_co2";
    public static final String QUEUE_DLQ_CO2_CONSULTAS = "dlq_co2_consultas";
    public static final String QUEUE_DLQ_LAINOA2      = "dlq_lainoa2_auditoria";

    public void configurar() {
        try (Connection connection = TLSConfig.crearFactory().newConnection();
             Channel channel = connection.createChannel()) {

            channel.exchangeDeclare(EXCHANGE_DLX_CO2, "direct", true);

            channel.exchangeDeclare(EXCHANGE_CO2_FANOUT, "fanout", true);
            declareQueueWithDlx(channel, QUEUE_CO2, QUEUE_DLQ_CO2);
            channel.queueBind(QUEUE_CO2, EXCHANGE_CO2_FANOUT, "");
            System.out.println("[CO2Config] FANOUT_CO2 -> co2 (DLX/DLQ) OK");

            channel.exchangeDeclare(EXCHANGE_CO2_RESULTADO, "direct", true);
            declareQueueWithDlx(channel, QUEUE_CO2_RESULTADO, QUEUE_DLQ_CO2_RESULTADO);
            channel.queueBind(QUEUE_CO2_RESULTADO, EXCHANGE_CO2_RESULTADO, QUEUE_CO2_RESULTADO);
            System.out.println("[CO2Config] RESULTADO_CO2 -> resultado_co2 (DLX/DLQ) OK");

            channel.exchangeDeclare(EXCHANGE_LAINOA2, "fanout", true);
            declareQueueWithDlx(channel, QUEUE_LAINOA2_AUDITORIA, QUEUE_DLQ_LAINOA2);
            channel.queueBind(QUEUE_LAINOA2_AUDITORIA, EXCHANGE_LAINOA2, "");
            System.out.println("[CO2Config] LAINOA2 -> q.lainoa2.auditoria (DLX/DLQ) OK");

            declareQueueWithDlx(channel, QUEUE_CO2_CONSULTAS, QUEUE_DLQ_CO2_CONSULTAS);
            System.out.println("[CO2Config] RPC consultas CO2 -> " + QUEUE_CO2_CONSULTAS + " (DLX/DLQ) OK");

            System.out.println("[CO2Config] TLS, ACK manual y DLX/DLQ configurados correctamente.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void declareQueueWithDlx(Channel channel, String queueName, String dlqName) throws IOException {
        declareDlq(channel, dlqName);
        channel.queueDeclare(queueName, true, false, false, dlxArgs(dlqName));
    }

    public static void declareDlq(Channel channel, String dlqName) throws IOException {
        channel.exchangeDeclare(EXCHANGE_DLX_CO2, "direct", true);
        channel.queueDeclare(dlqName, true, false, false, null);
        channel.queueBind(dlqName, EXCHANGE_DLX_CO2, dlqName);
    }

    public static Map<String, Object> dlxArgs(String dlqRoutingKey) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", EXCHANGE_DLX_CO2);
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
        new CO2StreamConfig().configurar();
    }
}
