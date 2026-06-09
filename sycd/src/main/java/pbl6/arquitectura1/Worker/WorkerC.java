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
import pbl6.arquitectura1.Publisher.KafkaStreamConfig;

public class WorkerC {

    static final double UMBRAL_DIST_MINIMA_COCHE = 300.0;
    static final double UMBRAL_DIST_COCHE_URBANO = 1000.0;
    static final double UMBRAL_VEL_MEDIA_COCHE_URBANO = 18.0;
    static final double UMBRAL_VEL_MAX_COCHE = 35.0;
    static final double UMBRAL_VEL_FINAL_COCHE = 30.0;

    ExecutorService pool;
    ConnectionFactory factory;

    public WorkerC() {
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

            channel.queueDeclare(KafkaStreamConfig.QUEUE_KOTXEA, true, false, false, null);
            channel.queueBind(KafkaStreamConfig.QUEUE_KOTXEA, KafkaStreamConfig.EXCHANGE_FANOUT, "");

            channel.queueDeclare(KafkaStreamConfig.QUEUE_EMAITZA, true, false, false, null);
            channel.queueBind(KafkaStreamConfig.QUEUE_EMAITZA, KafkaStreamConfig.EXCHANGE_EMAITZA, KafkaStreamConfig.QUEUE_EMAITZA);

            channel.basicQos(4);
            channel.basicConsume(KafkaStreamConfig.QUEUE_KOTXEA, false, new MiConsumer(channel));

            System.out.println("[WorkerC - Estrategia C] Instancia activa. Clasificación con media/máxima/final...");
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

        if (clasificarKotxea(resumen)) {
            String resultado = resumen.resultado("KOTXEA");
            synchronized (factory) {
                try (Connection conn = factory.newConnection(); Channel ch = conn.createChannel()) {
                    ch.basicPublish(KafkaStreamConfig.EXCHANGE_EMAITZA, KafkaStreamConfig.QUEUE_EMAITZA, null, resultado.getBytes());
                }
            }
            String horaActual = new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date());
            System.out.println(String.format(Locale.US,
                    "[%s] [WorkerC - Instancia] USER %d → KOTXEA (dist=%.0fm, vMedia=%.2f, vMax=%.2f, vFinal=%.2f)",
                    horaActual, resumen.userId, resumen.distanciaMetros, resumen.velocidadMediaKmh,
                    resumen.velocidadMaxKmh, resumen.velocidadFinalKmh));
        }
    }

    static boolean clasificarKotxea(ResumenWorker r) {
        if (r.distanciaMetros < UMBRAL_DIST_MINIMA_COCHE) return false;

        boolean cochePorPicoVelocidad = r.velocidadMaxKmh >= UMBRAL_VEL_MAX_COCHE;
        boolean cochePorMediaUrbana = r.distanciaMetros >= UMBRAL_DIST_COCHE_URBANO
                && r.velocidadMediaKmh >= UMBRAL_VEL_MEDIA_COCHE_URBANO;
        boolean cochePorVelocidadFinal = r.velocidadFinalKmh >= UMBRAL_VEL_FINAL_COCHE;

        return cochePorPicoVelocidad || cochePorMediaUrbana || cochePorVelocidadFinal;
    }

    public static void main(String[] args) {
        WorkerC worker = new WorkerC();
        worker.suscribir();
    }
}

class ResumenWorker {
    final int userId;
    final int empresaId;
    final double distanciaMetros;
    final double velocidadMediaKmh;
    final double velocidadMaxKmh;
    final double velocidadFinalKmh;
    final long tiempoSegundos;
    final String lat;
    final String lon;
    final String timestamp;
    final String sessionId;
    final int numPuntos;

    ResumenWorker(int userId, int empresaId, double distanciaMetros, double velocidadMediaKmh,
                  double velocidadMaxKmh, double velocidadFinalKmh, long tiempoSegundos,
                  String lat, String lon, String timestamp, String sessionId, int numPuntos) {
        this.userId = userId;
        this.empresaId = empresaId;
        this.distanciaMetros = distanciaMetros;
        this.velocidadMediaKmh = velocidadMediaKmh;
        this.velocidadMaxKmh = velocidadMaxKmh;
        this.velocidadFinalKmh = velocidadFinalKmh;
        this.tiempoSegundos = tiempoSegundos;
        this.lat = lat;
        this.lon = lon;
        this.timestamp = timestamp;
        this.sessionId = sessionId;
        this.numPuntos = numPuntos;
    }

    static ResumenWorker parse(String mensaje) {
        try {
            String[] p = mensaje.trim().split("\\s+");
            if (p.length >= 12) {
                return new ResumenWorker(
                        Integer.parseInt(p[0]),
                        Integer.parseInt(p[1]),
                        parseDouble(p[2]),
                        parseDouble(p[3]),
                        parseDouble(p[4]),
                        parseDouble(p[5]),
                        parseLong(p[6]),
                        p[7],
                        p[8],
                        p[9],
                        "SIN_SESSION".equals(p[10]) ? "" : p[10],
                        parseInt(p[11]));
            }

            // Compatibilidad con formato antiguo:
            // userId empresaId metros velocidadFinal tiempoSegundos lat lon timestamp sessionId
            if (p.length >= 8) {
                double velocidadFinal = parseDouble(p[3]);
                return new ResumenWorker(
                        Integer.parseInt(p[0]),
                        Integer.parseInt(p[1]),
                        parseDouble(p[2]),
                        velocidadFinal,
                        velocidadFinal,
                        velocidadFinal,
                        parseLong(p[4]),
                        p[5],
                        p[6],
                        p[7],
                        p.length >= 9 ? p[8] : "",
                        0);
            }
        } catch (Exception e) {
            System.err.println("[ResumenWorker] No se ha podido parsear: " + mensaje + " -> " + e.getMessage());
        }
        return null;
    }

    String resultado(String clasificacion) {
        return userId + " " + empresaId + " " + clasificacion + " " + lat + " " + lon + " " + timestamp + " " + sessionId;
    }

    private static double parseDouble(String value) {
        try { return Double.parseDouble(value); } catch (Exception e) { return 0.0; }
    }

    private static long parseLong(String value) {
        try { return Long.parseLong(value); } catch (Exception e) { return 0L; }
    }

    private static int parseInt(String value) {
        try { return Integer.parseInt(value); } catch (Exception e) { return 0; }
    }
}
