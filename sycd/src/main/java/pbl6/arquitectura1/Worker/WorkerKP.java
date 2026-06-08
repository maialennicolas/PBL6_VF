package pbl6.arquitectura1.Worker;

import java.io.IOException;
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
import pbl6.arquitectura1.Gestor.ParadasLoader;
import pbl6.arquitectura1.Publisher.KafkaStreamConfig;

public class WorkerKP {

    ExecutorService pool;
    ConnectionFactory factory;
    ParadasLoader paradasLoader;

    public WorkerKP() {
        try {
            this.factory = pbl6.arquitectura1.Config.TLSConfig.crearFactory();
        } catch (Exception e) {
            e.printStackTrace();
        }
        this.pool = Executors.newFixedThreadPool(4);
        paradasLoader = new ParadasLoader();
    }

    public void suscribir() {
        try (Connection connection = factory.newConnection()) {
            try (Channel channel = connection.createChannel()) {

            channel.exchangeDeclare(KafkaStreamConfig.EXCHANGE_FANOUT, "fanout", true);
            channel.exchangeDeclare(KafkaStreamConfig.EXCHANGE_EMAITZA, "direct", true);

            channel.queueDeclare(KafkaStreamConfig.QUEUE_KP, true, false, false, null);
            channel.queueBind(KafkaStreamConfig.QUEUE_KP, KafkaStreamConfig.EXCHANGE_FANOUT, "");

            channel.queueDeclare(KafkaStreamConfig.QUEUE_EMAITZA, true, false, false, null);
            channel.queueBind(KafkaStreamConfig.QUEUE_EMAITZA, KafkaStreamConfig.EXCHANGE_EMAITZA, KafkaStreamConfig.QUEUE_EMAITZA);

            channel.basicQos(4);
            channel.basicConsume(KafkaStreamConfig.QUEUE_KP, false, new MiConsumer(channel));

            System.out.println("[WorkerKP - Estrategia C] Instancia activa. Transporte público con media/máxima...");
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

    String clasificar(ResumenWorker resumen) {
        double lat = parseDouble(resumen.lat);
        double lon = parseDouble(resumen.lon);
        if (!paradasLoader.cercaDeParada(lat, lon)) return null;

        // Usamos media y máxima. La velocidad final puede ser baja si el usuario termina parado en una parada.
        double velocidadReferencia = Math.max(resumen.velocidadMediaKmh, resumen.velocidadMaxKmh * 0.70);

        if (resumen.distanciaMetros >= 500.0 && velocidadReferencia >= 15.0 && velocidadReferencia <= 65.0) return "BUS";
        if (resumen.distanciaMetros >= 1000.0 && velocidadReferencia > 65.0 && velocidadReferencia <= 200.0) return "TREN";
        return null;
    }

    public class MiConsumer extends DefaultConsumer {
        public MiConsumer(Channel channel) { super(channel); }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            String mensaje = new String(body, "UTF-8");
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
                    "[%s] [WorkerKP - Instancia] USER %d → %s (dist=%.0fm, vMedia=%.2f, vMax=%.2f)",
                    horaActual, resumen.userId, clasificacion, resumen.distanciaMetros,
                    resumen.velocidadMediaKmh, resumen.velocidadMaxKmh));
        }
    }

    private double parseDouble(String value) {
        try { return Double.parseDouble(value); } catch (Exception e) { return 0.0; }
    }

    public static void main(String[] args) {
        WorkerKP worker = new WorkerKP();
        worker.suscribir();
    }
}
