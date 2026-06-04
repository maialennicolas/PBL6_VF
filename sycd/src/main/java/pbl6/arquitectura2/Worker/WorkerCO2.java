package pbl6.arquitectura2.Worker;

import com.rabbitmq.client.*;

import pbl6.arquitectura2.Config.CO2StreamConfig;
import pbl6.arquitectura2.Config.TLSConfig;
import pbl6.arquitectura2.Gestor.CalculadoraCO2;
import pbl6.arquitectura2.Gestor.CalculadoraCO2.ResultadoCO2;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WorkerCO2 {

    static final int NUM_THREADS = 4;

    private final ConnectionFactory factory;
    private final ExecutorService   pool;

    public WorkerCO2() throws Exception {
        this.factory = TLSConfig.crearFactory();   // ← TLS
        this.pool    = Executors.newFixedThreadPool(NUM_THREADS);
    }

    public void suscribir() {
        try (Connection connection = factory.newConnection()) {
            Channel channel = connection.createChannel();

            channel.exchangeDeclare(CO2StreamConfig.EXCHANGE_CO2_FANOUT,    "fanout", true);
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

            System.out.println("[WorkerCO2] Thread Pool (" + NUM_THREADS +
                    " threads) esperando en Q:co2... [TLS aktibo]");
            System.out.println("──────────────────────────────────────────────────────");

            synchronized (this) {
                try { wait(); } catch (InterruptedException e) { e.printStackTrace(); }
            }
            pool.shutdown();
            channel.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void parar() { notify(); }

    public class MiConsumer extends DefaultConsumer {
        public MiConsumer(Channel channel) { super(channel); }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope,
                AMQP.BasicProperties properties, byte[] body) throws IOException {
            String mensaje = new String(body, "UTF-8");
            pool.execute(() -> {
                try {
                    procesarMensaje(mensaje);
                    synchronized (getChannel()) {
                        getChannel().basicAck(envelope.getDeliveryTag(), false);
                    }
                } catch (Exception e) { e.printStackTrace(); }
            });
        }
    }

    private void procesarMensaje(String mensaje) throws Exception {
        ResultadoCO2 resultado;
        try {
            resultado = CalculadoraCO2.calcular(mensaje);
        } catch (IllegalArgumentException e) {
            System.err.println("[WorkerCO2] Mensaje inválido: " + mensaje);
            return;
        }

        System.out.printf("[WorkerCO2][%s] %s%n", Thread.currentThread().getName(), resultado);

        String msgResultado = resultado.toLainoa2();

        synchronized (factory) {
            try (Connection conn = factory.newConnection();
                 Channel ch = conn.createChannel()) {
                ch.basicPublish(
                        CO2StreamConfig.EXCHANGE_CO2_RESULTADO,
                        CO2StreamConfig.QUEUE_CO2_RESULTADO,
                        null,
                        msgResultado.getBytes());
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("[WorkerCO2] Iniciando con TLS. Pulsa ENTER para parar.");
        try {
            WorkerCO2 worker = new WorkerCO2();
            Scanner teclado = new Scanner(System.in);
            new Thread(() -> { teclado.nextLine(); worker.parar(); teclado.close(); }).start();
            worker.suscribir();
        } catch (Exception e) {
            System.err.println("[WorkerCO2] Error TLS: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
