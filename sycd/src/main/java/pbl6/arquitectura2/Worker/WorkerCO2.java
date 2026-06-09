package pbl6.arquitectura2.Worker;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import pbl6.arquitectura2.Config.CO2StreamConfig;
import pbl6.arquitectura2.Config.TLSConfig;
import pbl6.arquitectura2.Gestor.CalculadoraCO2;
import pbl6.arquitectura2.Gestor.CalculadoraCO2.ResultadoCO2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WorkerCO2 {

    static final int NUM_THREADS = 4;

    private final ConnectionFactory factory;
    private final ExecutorService pool;

    public WorkerCO2() throws Exception {
        this.factory = TLSConfig.crearFactory();
        this.pool = Executors.newFixedThreadPool(NUM_THREADS);
    }

    public void suscribir() {
        try (Connection connection = factory.newConnection();
     Channel channel = connection.createChannel()) {

            channel.exchangeDeclare(CO2StreamConfig.EXCHANGE_CO2_FANOUT, "fanout", true);
            channel.exchangeDeclare(CO2StreamConfig.EXCHANGE_CO2_RESULTADO, "direct", true);

            channel.queueDeclare(CO2StreamConfig.QUEUE_CO2, true, false, false, null);
            channel.queueBind(CO2StreamConfig.QUEUE_CO2,
                    CO2StreamConfig.EXCHANGE_CO2_FANOUT, "");

            channel.queueDeclare(CO2StreamConfig.QUEUE_CO2_RESULTADO, true, false, false, null);
            channel.queueBind(CO2StreamConfig.QUEUE_CO2_RESULTADO,
                    CO2StreamConfig.EXCHANGE_CO2_RESULTADO,
                    CO2StreamConfig.QUEUE_CO2_RESULTADO);

            channel.basicQos(NUM_THREADS);
            channel.basicConsume(CO2StreamConfig.QUEUE_CO2, false, new MiConsumer(channel));

            System.out.println("[WorkerCO2 - Arquitectura 2] Esperando resultados finales de Arquitectura 1 en Q:co2...");
            System.out.println("[WorkerCO2 - Arquitectura 2] Calcula CO2/puntos con BD, modelo de coche y carpool.");
            System.out.println("──────────────────────────────────────────────────────");

            new CountDownLatch(1).await();

        } catch (Exception e) {
            e.printStackTrace();
            Thread.currentThread().interrupt(); // <--- GEHITU LERRO HAU
        } finally {
            pool.shutdownNow();
        }
    }

    public class MiConsumer extends DefaultConsumer {
        public MiConsumer(Channel channel) {
            super(channel);
        }

        @Override
        public void handleDelivery(String consumerTag,
                                   Envelope envelope,
                                   AMQP.BasicProperties properties,
                                   byte[] body) throws IOException {
            String mensaje = new String(body, StandardCharsets.UTF_8);
            pool.execute(() -> {
                try {
                    ResultadoCO2 resultado = CalculadoraCO2.calcularYActualizar(mensaje);
                    System.out.printf("[WorkerCO2 - Arquitectura 2][%s] %s%n",
                            Thread.currentThread().getName(), resultado);
                    publicarResultado(resultado);
                    synchronized (getChannel()) {
                        getChannel().basicAck(envelope.getDeliveryTag(), false);
                    }
                } catch (Exception e) {
                    System.err.println("[WorkerCO2 - Arquitectura 2] Error procesando mensaje: " + mensaje);
                    e.printStackTrace();
                    try {
                        synchronized (getChannel()) {
                            getChannel().basicNack(envelope.getDeliveryTag(), false, false);
                        }
                    } catch (Exception nackError) {
                        nackError.printStackTrace();
                    }
                }
            });
        }
    }

    private void publicarResultado(ResultadoCO2 resultado) throws Exception {
        try (Connection conn = factory.newConnection();
             Channel ch = conn.createChannel()) {
            ch.exchangeDeclare(CO2StreamConfig.EXCHANGE_CO2_RESULTADO, "direct", true);
            ch.basicPublish(
                    CO2StreamConfig.EXCHANGE_CO2_RESULTADO,
                    CO2StreamConfig.QUEUE_CO2_RESULTADO,
                    null,
                    resultado.toLainoa2().getBytes(StandardCharsets.UTF_8));
        }
    }

    public static void main(String[] args) {
        try {
            new WorkerCO2().suscribir();
        } catch (Exception e) {
            System.err.println("[WorkerCO2 - Arquitectura 2] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
