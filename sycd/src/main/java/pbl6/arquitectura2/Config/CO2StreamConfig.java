package pbl6.arquitectura2.Config;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

public class CO2StreamConfig {

    public static final String EXCHANGE_CO2_FANOUT    = "fanout_co2";
    public static final String QUEUE_CO2              = "co2";
    public static final String EXCHANGE_CO2_RESULTADO = "resultado_co2";
    public static final String QUEUE_CO2_RESULTADO    = "resultado_co2";
    public static final String EXCHANGE_LAINOA2       = "lainoa2";

    public void configurar() {
        try (Connection connection = TLSConfig.crearFactory().newConnection();
             Channel channel = connection.createChannel()) {

            channel.exchangeDeclare(EXCHANGE_CO2_FANOUT, "fanout", true);
            channel.queueDeclare(QUEUE_CO2, true, false, false, null);
            channel.queueBind(QUEUE_CO2, EXCHANGE_CO2_FANOUT, "");
            System.out.println("[CO2Config] FANOUT_CO2 → co2 OK");

            channel.exchangeDeclare(EXCHANGE_CO2_RESULTADO, "direct", true);
            channel.queueDeclare(QUEUE_CO2_RESULTADO, true, false, false, null);
            channel.queueBind(QUEUE_CO2_RESULTADO, EXCHANGE_CO2_RESULTADO, QUEUE_CO2_RESULTADO);
            System.out.println("[CO2Config] RESULTADO_CO2 → resultado_co2 OK");

            channel.exchangeDeclare(EXCHANGE_LAINOA2, "fanout", true);
            System.out.println("[CO2Config] LAINOA2 (fanout) OK");

            System.out.println("[CO2Config] ✔ TLS-rekin konfiguratuta.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new CO2StreamConfig().configurar();
    }
}
