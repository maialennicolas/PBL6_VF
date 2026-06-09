package pbl6.arquitectura1.Worker;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
        
        // ESTRATEGIA B: Obtener dinámicamente los cores de la máquina local
        int cores = Runtime.getRuntime().availableProcessors();
        
        // Inicialización adaptiva del ThreadPoolExecutor elástico
        this.pool = new ThreadPoolExecutor(
            cores,                                    // Hilos mínimos activos (Core size)
            cores * 2,                                // Hilos máximos en picos (Max size)
            60L, TimeUnit.SECONDS,                    // Tiempo de vida de hilos extra inactivos
            new LinkedBlockingQueue<>(50),            // Cola interna de retención local
            new ThreadPoolExecutor.CallerRunsPolicy() // Mecanismo de Backpressure protector
        );
    }

    public void suscribir() {
        try (Connection connection = factory.newConnection()) {
            Channel channel = connection.createChannel();

            channel.exchangeDeclare(KafkaStreamConfig.EXCHANGE_FANOUT, "fanout", true);
            channel.exchangeDeclare(KafkaStreamConfig.EXCHANGE_EMAITZA, "direct", true);

            channel.queueDeclare(KafkaStreamConfig.QUEUE_PUBLIKO, true, false, false, null);
            channel.queueBind(KafkaStreamConfig.QUEUE_PUBLIKO, KafkaStreamConfig.EXCHANGE_FANOUT, "");

            channel.queueDeclare(KafkaStreamConfig.QUEUE_EMAITZA, true, false, false, null);
            channel.queueBind(KafkaStreamConfig.QUEUE_EMAITZA, KafkaStreamConfig.EXCHANGE_EMAITZA, KafkaStreamConfig.QUEUE_EMAITZA);

            // ESTRATEGIA B: QoS balanceado según hardware local
            int cores = Runtime.getRuntime().availableProcessors();
            channel.basicQos(cores * 2);
            channel.basicConsume(KafkaStreamConfig.QUEUE_PUBLIKO, false, new MiConsumer(channel));

            System.out.println("[WorkerP - Estrategia B] Esperando mensajes... Cores detectados: " + cores);
            synchronized (this) {
                try { wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            pool.shutdown();
            channel.close();
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    public synchronized void parar() { notify(); }

    // Clasificación basada en array directo de Strings
    static String clasificar(double metros, double velocidad) {
        // Filtros equivalentes de la lógica de negocio urbana
        if (metros >= 300.0 && velocidad >= 30.0) return null;

        if (velocidad < 6.0)  return "OINEZ";
        if (velocidad < 15.0) return "KORRIKA";
        return metros < 500.0 ? "TXIRRINA" : "PATINETE";
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
        String[] p = mensaje.split(" ");
        if (p.length < 8) return;

        int    userId    = Integer.parseInt(p[0]);
        int    empresaId = Integer.parseInt(p[1]);
        double metros    = Double.parseDouble(p[2]);
        double velocidad = Double.parseDouble(p[3]);
        String lat       = p[5];
        String lon       = p[6];
        String timestamp = p[7];

        String clasificacion = clasificar(metros, velocidad);

        if (clasificacion != null) {
            String resultado = userId + " " + empresaId + " " + clasificacion + " " + lat + " " + lon + " " + timestamp;
            synchronized (factory) {
                try (Connection conn = factory.newConnection(); Channel ch = conn.createChannel()) {
                    ch.basicPublish(KafkaStreamConfig.EXCHANGE_EMAITZA, KafkaStreamConfig.QUEUE_EMAITZA, null, resultado.getBytes());
                }
            }
            String horaActual = new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date());
            System.out.println(String.format(Locale.US,
                    "[%s] [WorkerP - Estrategia B] USER %d → %s (dist=%.0fm, velocidad=%.2f)",
                    horaActual, userId, clasificacion, metros, velocidad));
        }
    }

    public static void main(String[] args) {
        WorkerP worker = new WorkerP();
        worker.suscribir();
    }
}