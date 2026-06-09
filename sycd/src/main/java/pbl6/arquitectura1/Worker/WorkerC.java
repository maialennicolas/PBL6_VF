package pbl6.arquitectura1.Worker;

import java.io.IOException;
import java.util.Scanner;
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

public class WorkerC {

    static final double UMBRAL_VEL  = 30.0;
    static final double UMBRAL_DIST = 5.0;
    
    ExecutorService pool;
    ConnectionFactory factory;

    public WorkerC() {
        try {
            this.factory = pbl6.arquitectura1.Config.TLSConfig.crearFactory();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // ESTRATEGIA B: Obtener dinámicamente los cores de la máquina local
        int cores = Runtime.getRuntime().availableProcessors();
        
        // Inicialización adaptiva del ThreadPoolExecutor
        this.pool = new ThreadPoolExecutor(
            cores,                                    // Core pool size
            cores * 2,                                // Maximum pool size
            60L, TimeUnit.SECONDS,                    // Keep-alive time hilos extra
            new LinkedBlockingQueue<>(50),            // Cola interna fija de retención
            new ThreadPoolExecutor.CallerRunsPolicy() // Saturación: backpressure local frente a avalanchas
        );
    }

    public void suscribir() {
        try (Connection connection = factory.newConnection()) {
            Channel channel = connection.createChannel();

            channel.exchangeDeclare(KafkaStreamConfig.EXCHANGE_FANOUT, "fanout", true);
            channel.exchangeDeclare(KafkaStreamConfig.EXCHANGE_EMAITZA, "direct", true);

            channel.queueDeclare(KafkaStreamConfig.QUEUE_KOTXEA, true, false, false, null);
            channel.queueBind(KafkaStreamConfig.QUEUE_KOTXEA, KafkaStreamConfig.EXCHANGE_FANOUT, "");

            channel.queueDeclare(KafkaStreamConfig.QUEUE_EMAITZA, true, false, false, null);
            channel.queueBind(KafkaStreamConfig.QUEUE_EMAITZA, KafkaStreamConfig.EXCHANGE_EMAITZA, KafkaStreamConfig.QUEUE_EMAITZA);

            // ESTRATEGIA B: basicQos adaptado a la potencia de la CPU local
            int cores = Runtime.getRuntime().availableProcessors();
            channel.basicQos(cores * 2);
            channel.basicConsume(KafkaStreamConfig.QUEUE_KOTXEA, false, new MiConsumer(channel));

            System.out.println("[WorkerC - Estrategia B] Esperando en Q:kotxea... Cores detectados: " + cores);
            synchronized (this) {
                try { wait(); } catch (InterruptedException e) { e.printStackTrace(); }
            }
            pool.shutdown();
            channel.close();
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    public synchronized void parar() { notify(); }

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

        boolean esKotxea = velocidad > UMBRAL_VEL && metros > UMBRAL_DIST;

        if (esKotxea) {
            String resultado = userId + " " + empresaId + " KOTXEA " + lat + " " + lon + " " + timestamp;
            synchronized (factory) {
                try (Connection conn = factory.newConnection(); Channel ch = conn.createChannel()) {
                    ch.basicPublish(KafkaStreamConfig.EXCHANGE_EMAITZA, KafkaStreamConfig.QUEUE_EMAITZA, null, resultado.getBytes());
                }
            }
            String horaActual = new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date());
            System.out.println("[" + horaActual + "] [WorkerC] USER " + userId + " → KOTXEA");
        }
    }

    public static void main(String[] args) {
        Scanner teclado = new Scanner(System.in);
        System.out.println("Pulsa ENTER para parar.");
        WorkerC worker = new WorkerC();
        new Thread(() -> { teclado.nextLine(); worker.parar(); teclado.close(); }).start();
        worker.suscribir();
    }
}
