package pbl6.arquitectura1.Worker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import pbl6.arquitectura1.Publisher.KafkaStreamConfig;

public class WorkerP {

    ExecutorService pool;
    ConnectionFactory factory;

    public WorkerP() {
        try {
            this.factory = pbl6.arquitectura1.Config.TLSConfig.crearFactory();
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.pool = Executors.newFixedThreadPool(4);
    }

    public void suscribir() {
        try (Connection connection = factory.newConnection()) {
            try (Channel channel = connection.createChannel()) {

            channel.exchangeDeclare(KafkaStreamConfig.EXCHANGE_FANOUT, "fanout", true);
            channel.exchangeDeclare(KafkaStreamConfig.EXCHANGE_EMAITZA, "direct", true);

            channel.queueDeclare(KafkaStreamConfig.QUEUE_PUBLIKO, true, false, false, null);
            channel.queueBind(KafkaStreamConfig.QUEUE_PUBLIKO, KafkaStreamConfig.EXCHANGE_FANOUT, "");

            channel.queueDeclare(KafkaStreamConfig.QUEUE_EMAITZA, true, false, false, null);
            channel.queueBind(KafkaStreamConfig.QUEUE_EMAITZA, KafkaStreamConfig.EXCHANGE_EMAITZA, KafkaStreamConfig.QUEUE_EMAITZA);

            channel.basicQos(4);
            channel.basicConsume(KafkaStreamConfig.QUEUE_PUBLIKO, false, new MiConsumer(channel));

            System.out.println("[WorkerP - Estrategia C] Instancia activa. Clasificación personal con media/máxima...");
            synchronized (this) {
                try { wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            pool.shutdown();
            channel.close();
        }
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    public synchronized void parar() { notifyAll(); }

    static String clasificar(ResumenWorker resumen) {
        double velocidad = resumen.velocidadMediaKmh > 0.0
                ? resumen.velocidadMediaKmh
                : Math.max(resumen.velocidadFinalKmh, resumen.velocidadMaxKmh);

        // Si parece coche urbano o coche claro, lo dejamos para WorkerC.
        if (resumen.distanciaMetros >= 1000.0 && velocidad >= 18.0) return null;
        if (resumen.distanciaMetros >= 300.0 && resumen.velocidadMaxKmh >= 35.0) return null;
        if (resumen.distanciaMetros >= 300.0 && resumen.velocidadFinalKmh >= 30.0) return null;

        if (velocidad < 6.0)  return "OINEZ";
        if (velocidad < 15.0) return "KORRIKA";
        return resumen.distanciaMetros < 500.0 ? "TXIRRINA" : "PATINETE";
    }

    public class MiConsumer extends DefaultConsumer {
        public MiConsumer(Channel channel) { super(channel); }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            String mensaje = new String(body, StandardCharsets.UTF_8);
            pool.execute(() -> {
                try {
                    procesarMensaje(mensaje);
                    synchronized (getChannel()) {
                        getChannel().basicAck(envelope.getDeliveryTag(), false);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    try {
                        synchronized (getChannel()) {
                            getChannel().basicNack(envelope.getDeliveryTag(), false, false);
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            });
        }
    }

    private void procesarMensaje(String mensaje) throws IOException, TimeoutException {
        ResumenWorker resumen = ResumenWorker.parse(mensaje);
        if (resumen == null) return;

        String clasificacion = clasificar(resumen);

        if (clasificacion != null) {
            String resultado = resumen.resultado(clasificacion);
            synchronized (factory) {
                try (Connection conn = factory.newConnection(); Channel ch = conn.createChannel()) {
                    ch.basicPublish(KafkaStreamConfig.EXCHANGE_EMAITZA, KafkaStreamConfig.QUEUE_EMAITZA, null, resultado.getBytes());
                }
            }
            String horaActual = new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date());
            System.out.println(String.format(Locale.US,
                    "[%s] [WorkerP - Instancia] USER %d → %s (dist=%.0fm, vMedia=%.2f, vMax=%.2f, vFinal=%.2f)",
                    horaActual, resumen.userId, clasificacion, resumen.distanciaMetros,
                    resumen.velocidadMediaKmh, resumen.velocidadMaxKmh, resumen.velocidadFinalKmh));
        }
    }

    public static void main(String[] args) {
        WorkerP worker = new WorkerP();
        worker.suscribir();
    }
}
