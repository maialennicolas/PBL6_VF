package pbl6.arquitectura1.Resultado;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import pbl6.arquitectura1.Publisher.KafkaStreamConfig;
import pbl6.arquitectura2.Config.CO2StreamConfig;

public class ResultWorker {

    static final String EXCHANGE_LAINOA = "lainoa";

    ConnectionFactory factory;

    // userId:sessionId -> mensaje resultado final
    Map<String, String> resultados = new ConcurrentHashMap<>();

    public ResultWorker() {
        try {
            this.factory = pbl6.arquitectura1.Config.TLSConfig.crearFactory();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void suscribir() {

        try (Connection connection = factory.newConnection()) {

            Channel channel = connection.createChannel();

            channel.exchangeDeclare(
                    KafkaStreamConfig.EXCHANGE_EMAITZA,
                    "direct",
                    true);

            channel.exchangeDeclare(
                    EXCHANGE_LAINOA,
                    "fanout",
                    true);

            channel.exchangeDeclare(
                    CO2StreamConfig.EXCHANGE_CO2_FANOUT,
                    "fanout",
                    true);

            channel.queueDeclare(
                    KafkaStreamConfig.QUEUE_EMAITZA,
                    true,
                    false,
                    false,
                    null);

            channel.queueBind(
                    KafkaStreamConfig.QUEUE_EMAITZA,
                    KafkaStreamConfig.EXCHANGE_EMAITZA,
                    KafkaStreamConfig.QUEUE_EMAITZA);

            channel.basicQos(1);

            channel.basicConsume(
                    KafkaStreamConfig.QUEUE_EMAITZA,
                    false,
                    new MiConsumer(channel));

            System.out.println("[ResultWorker] Esperando resultados de Arquitectura 1...");
            System.out.println("[ResultWorker] El CO2 se delega a Arquitectura 2 mediante fanout_co2.");

            synchronized (this) {

                try {
                    wait();

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();
                }
            }

            channel.close();

        } catch (IOException | TimeoutException e) {

            e.printStackTrace();
        }
    }

    public synchronized void parar() {

        notify();
    }

    public class MiConsumer extends DefaultConsumer {

        public MiConsumer(Channel channel) {

            super(channel);
        }

        @Override
        public void handleDelivery(
                String consumerTag,
                Envelope envelope,
                AMQP.BasicProperties properties,
                byte[] body) throws IOException {

            String mensaje = new String(body, "UTF-8");

            String[] p = mensaje.trim().split("\\s+");

            if (p.length < 6) {

                getChannel().basicAck(
                        envelope.getDeliveryTag(),
                        false);

                return;
            }

            int userId = Integer.parseInt(p[0]);
            String sessionId = p.length >= 7 ? p[6] : "";
            String resultadoKey = userId + ":" + sessionId;

            String clasificacion = p[2];

            if (clasificacion.equals("BUS") || clasificacion.equals("TREN")) {

                guardarSiMejora(resultadoKey, mensaje);

            } else if (clasificacion.equals("KOTXEA")) {

                // Esperar por si también llega BUS/TREN.
                new Thread(() -> {

                    try {

                        Thread.sleep(2000);
                        guardarSiMejora(resultadoKey, mensaje);

                    } catch (Exception e) {

                        e.printStackTrace();
                    }

                }).start();

            } else {

                guardarSiMejora(resultadoKey, mensaje);
            }

            getChannel().basicAck(
                    envelope.getDeliveryTag(),
                    false);
        }
    }

    private void guardarSiMejora(String resultadoKey, String mensaje) {
        String actual = resultados.get(resultadoKey);
        String nuevaClasificacion = clasificacion(mensaje);
        String clasificacionActual = clasificacion(actual);

        if (actual == null || prioridad(nuevaClasificacion) > prioridad(clasificacionActual)) {
            resultados.put(resultadoKey, mensaje);
            imprimirResultado(mensaje);
        }
    }

    private String clasificacion(String mensaje) {
        if (mensaje == null || mensaje.isBlank()) return "";
        String[] p = mensaje.trim().split("\\s+");
        return p.length >= 3 ? p[2] : "";
    }

    private int prioridad(String clasificacion) {
        if (clasificacion == null) return 0;
        return switch (clasificacion) {
            case "BUS", "TREN" -> 3;
            case "KOTXEA" -> 2;
            case "OINEZ", "KORRIKA", "TXIRRINA", "PATINETE" -> 1;
            default -> 0;
        };
    }

    private void imprimirResultado(String mensaje) {

        String[] p = mensaje.trim().split("\\s+");

        int userId = Integer.parseInt(p[0]);

        String clasificacion = p[2];

        publicarEnArquitectura2(mensaje);

        System.out.println(
                "[ResultWorker] USER "
                        + userId
                        + " → "
                        + clasificacion
                        + " | enviado a Arquitectura 2 para CO2/puntos");

        System.out.println(
                "[Lainoa] "
                        + mensaje);
    }

    private void publicarEnArquitectura2(String mensaje) {
        try (Connection conn = factory.newConnection();
             Channel ch = conn.createChannel()) {
            ch.exchangeDeclare(CO2StreamConfig.EXCHANGE_CO2_FANOUT, "fanout", true);
            ch.basicPublish(
                    CO2StreamConfig.EXCHANGE_CO2_FANOUT,
                    "",
                    null,
                    mensaje.getBytes("UTF-8"));
            System.out.println("[ResultWorker→Arquitectura2] fanout_co2: " + mensaje);
        } catch (Exception e) {
            System.err.println("[ResultWorker] No se ha podido enviar a Arquitectura 2: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {

        ResultWorker worker = new ResultWorker();
        worker.suscribir();
    }
}
