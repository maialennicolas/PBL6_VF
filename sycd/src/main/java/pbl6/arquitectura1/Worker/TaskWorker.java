package pbl6.arquitectura1.Worker;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import pbl6.arquitectura1.Gestor.GestorDatos;
import pbl6.arquitectura1.Publisher.KafkaStreamConfig;
import pbl6.arquitectura1.Gestor.Medidor;

public class TaskWorker {

    static final double MAX_REASONABLE_SPEED_KMH = 250.0;
    static final double MIN_DISTANCE_TO_COUNT_METERS = 3.0;

    ConnectionFactory factory;
    GestorDatos gestorDatos;
    Map<String, EstadoUsuario> usuarios;
    ExecutorService pool;
    Channel channel;

    public TaskWorker() {
        try {
            this.factory = pbl6.arquitectura1.Config.TLSConfig.crearFactory();
        } catch (Exception e) {
            e.printStackTrace();
        }
        gestorDatos = new GestorDatos();
        usuarios = new ConcurrentHashMap<>();

        // TaskWorker agrega puntos. Usamos un único hilo para no procesar el END antes que puntos anteriores.
        this.pool = Executors.newSingleThreadExecutor();
    }

    public void suscribir() {
        try (Connection connection = factory.newConnection()) {
            channel = connection.createChannel();

            // DLX y DLQ (Fidagarritasun azpiegitura)
            channel.exchangeDeclare(KafkaStreamConfig.EXCHANGE_DLX, "direct", true);
            channel.queueDeclare(KafkaStreamConfig.QUEUE_DLQ, true, false, false, null);
            channel.queueBind(KafkaStreamConfig.QUEUE_DLQ, KafkaStreamConfig.EXCHANGE_DLX, KafkaStreamConfig.QUEUE_DLQ);

            channel.exchangeDeclare(KafkaStreamConfig.EXCHANGE_STREAM, "direct", true);
            channel.exchangeDeclare(KafkaStreamConfig.EXCHANGE_FANOUT, "fanout", true);

            // Tarea ilara DLX-rekin lotuta
            Map<String, Object> argsTarea = new HashMap<>();
            argsTarea.put("x-dead-letter-exchange", KafkaStreamConfig.EXCHANGE_DLX);
            argsTarea.put("x-dead-letter-routing-key", KafkaStreamConfig.QUEUE_DLQ);
            channel.queueDeclare(KafkaStreamConfig.QUEUE_TAREA, true, false, false, argsTarea);
            channel.queueBind(KafkaStreamConfig.QUEUE_TAREA, KafkaStreamConfig.EXCHANGE_STREAM, KafkaStreamConfig.QUEUE_TAREA);

            channel.basicQos(1);
            channel.basicConsume(KafkaStreamConfig.QUEUE_TAREA, false, new MiConsumer(channel));

            System.out.println("[TaskWorker] Esperando puntos reales en Q:tarea... [agregación por userId+sessionId]");

            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            pool.shutdown();
            channel.close();

        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    public synchronized void parar() {
        notifyAll();
    }

    public class MiConsumer extends DefaultConsumer {
        public MiConsumer(Channel channel) { super(channel); }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            String mensaje = new String(body, "UTF-8");
            //
            System.out.println("[TaskWorker][RAW] Mensaje recibido: " + mensaje);
            //
            pool.execute(() -> {
                try {
                    procesarMensaje(mensaje);
                    synchronized (getChannel()) {
                        getChannel().basicAck(envelope.getDeliveryTag(), false);
                    }
                } catch (Exception e) {
                    System.err.println("[TaskWorker] Error procesando mensaje: " + mensaje);
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

    private final Medidor medidor = new Medidor("TaskWorker");

    private void procesarMensaje(String mensaje) throws IOException {
    long t0 = System.currentTimeMillis();
    String[] p = mensaje.trim().split("\\s+");

    if (p.length < 7) return;

    int userId = Integer.parseInt(p[0]);
    int empresaId = Integer.parseInt(p[1]);
    double lat = parseDouble(p[2]);
    double lon = parseDouble(p[3]);
    double velocidad = parseDouble(p[4]);
    double metrosRecibidos = parseDouble(p[5]);
    boolean terminado = Boolean.parseBoolean(p[6]);
    String sessionId = p.length >= 8 ? p[7] : "";
    long eventTimestamp = p.length >= 9 ? parseTimestamp(p[8]) : System.currentTimeMillis();

    System.out.printf(
            Locale.US,
            "[TaskWorker][PUNTO] user=%d empresa=%d session=%s lat=%.6f lon=%.6f velocidad=%.2f km/h metrosWeb=%.2f terminado=%s timestamp=%d%n",
            userId,
            empresaId,
            sessionId,
            lat,
            lon,
            velocidad,
            metrosRecibidos,
            terminado,
            eventTimestamp
    );

    String userKey = userId + ":" + sessionId;

    EstadoUsuario estado = usuarios.computeIfAbsent(userKey, key -> {
        EstadoUsuario nuevo = new EstadoUsuario();
        nuevo.userId = userId;
        nuevo.empresaId = empresaId;
        nuevo.sessionId = sessionId;
        nuevo.timestampInicio = eventTimestamp;
        return nuevo;
    });

    ResumenViaje resumen = null;

    synchronized (estado) {
        estado.userId = userId;
        estado.empresaId = empresaId;
        estado.sessionId = sessionId == null ? "" : sessionId;
        estado.maxMetrosRecibidos = Math.max(estado.maxMetrosRecibidos, Math.max(0.0, metrosRecibidos));
        estado.registrarPunto(new Punto(lat, lon, Math.max(0.0, velocidad), eventTimestamp));

        if (terminado) {
            resumen = estado.calcularResumen();
            usuarios.remove(userKey);
        }
    }

    if (resumen != null) {
        publicarResumen(resumen);
    }

    medidor.registrar(System.currentTimeMillis() - t0);
}

    private void publicarResumen(ResumenViaje resumen) throws IOException {
        String fecha = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new java.util.Date(resumen.timestampFinal));

        // Formato enviado a WorkerC / WorkerP / WorkerKP:
        // userId empresaId distanciaMetros velocidadMediaKmh velocidadMaxKmh velocidadFinalKmh
        // tiempoSegundos latFinal lonFinal timestampFinal sessionId numPuntos
        String linea = String.format(Locale.US,
                "%d %d %.2f %.2f %.2f %.2f %d %.6f %.6f %d %s %d",
                resumen.userId,
                resumen.empresaId,
                resumen.distanciaMetros,
                resumen.velocidadMediaKmh,
                resumen.velocidadMaxKmh,
                resumen.velocidadFinalKmh,
                resumen.tiempoSegundos,
                resumen.latFinal,
                resumen.lonFinal,
                resumen.timestampFinal,
                resumen.sessionId == null || resumen.sessionId.isBlank() ? "SIN_SESSION" : resumen.sessionId,
                resumen.numPuntos);

        System.out.println("\n>>>>>>>>>>>>>>>>>>>>>>>" +
                "\nUSER " + resumen.userId + " HA TERMINADO SU RUTA" +
                "\nEmpresa ID: " + resumen.empresaId +
                "\nPuntos recibidos: " + resumen.numPuntos +
                "\nDistancia calculada: " + String.format(Locale.US, "%.2f", resumen.distanciaMetros) + " m" +
                "\nVelocidad media: " + String.format(Locale.US, "%.2f", resumen.velocidadMediaKmh) + " km/h" +
                "\nVelocidad máxima: " + String.format(Locale.US, "%.2f", resumen.velocidadMaxKmh) + " km/h" +
                "\nVelocidad final: " + String.format(Locale.US, "%.2f", resumen.velocidadFinalKmh) + " km/h" +
                "\n* Tiempo total: " + formatearTiempo(resumen.tiempoSegundos * 1000L) +
                "\n* Fecha llegada: " + fecha +
                "\n>>>>>>>>>>>>>>>>>>>>>>>");

        channel.basicPublish(KafkaStreamConfig.EXCHANGE_FANOUT, "", null, linea.getBytes());
    }

    private long parseTimestamp(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String formatearTiempo(long ms) {
        long segundos = ms / 1000;
        long horas = segundos / 3600;
        long minutos = (segundos % 3600) / 60;
        long segs = segundos % 60;
        if (horas > 0) return horas + "h " + minutos + " minutos " + segs + " segundos";
        else if (minutos > 0) return minutos + " minutos " + segs + " segundos";
        else return segs + " segundos";
    }

    public static void main(String[] args) {
        TaskWorker worker = new TaskWorker();
        worker.suscribir();
    }

    static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double r = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}

class EstadoUsuario {
    int userId;
    int empresaId;
    String sessionId = "";
    long timestampInicio;
    double maxMetrosRecibidos = 0.0;
    final List<Punto> puntos = new ArrayList<>();

    void registrarPunto(Punto punto) {
        if (punto == null) return;
        if (!puntos.isEmpty()) {
            Punto ultimo = puntos.get(puntos.size() - 1);
            boolean mismoTimestamp = ultimo.timestamp == punto.timestamp;
            boolean mismaPosicion = TaskWorker.haversineMeters(ultimo.lat, ultimo.lon, punto.lat, punto.lon) < 0.5;
            if (mismoTimestamp && mismaPosicion) {
                puntos.set(puntos.size() - 1, punto);
                return;
            }
        }
        puntos.add(punto);
    }

    ResumenViaje calcularResumen() {
        List<Punto> ordenados = new ArrayList<>(puntos);
        ordenados.sort(Comparator.comparingLong(p -> p.timestamp));

        double distanciaGps = 0.0;
        double velocidadMaxKmh = 0.0;
        double velocidadFinalKmh = 0.0;
        long inicio = timestampInicio;
        long fin = timestampInicio;
        double latFinal = 0.0;
        double lonFinal = 0.0;

        if (!ordenados.isEmpty()) {
            inicio = ordenados.get(0).timestamp;
            fin = ordenados.get(ordenados.size() - 1).timestamp;
            latFinal = ordenados.get(ordenados.size() - 1).lat;
            lonFinal = ordenados.get(ordenados.size() - 1).lon;
            velocidadFinalKmh = Math.max(0.0, ordenados.get(ordenados.size() - 1).velocidadKmh);
        }

        for (Punto punto : ordenados) {
            if (punto.velocidadKmh > 0.0 && punto.velocidadKmh <= TaskWorker.MAX_REASONABLE_SPEED_KMH) {
                velocidadMaxKmh = Math.max(velocidadMaxKmh, punto.velocidadKmh);
            }
        }

        for (int i = 1; i < ordenados.size(); i++) {
            Punto anterior = ordenados.get(i - 1);
            Punto actual = ordenados.get(i);
            double segmentoMetros = TaskWorker.haversineMeters(anterior.lat, anterior.lon, actual.lat, actual.lon);
            long segmentoMs = Math.max(0L, actual.timestamp - anterior.timestamp);

            if (segmentoMetros >= TaskWorker.MIN_DISTANCE_TO_COUNT_METERS) {
                distanciaGps += segmentoMetros;
            }

            if (segmentoMs > 0 && segmentoMetros >= TaskWorker.MIN_DISTANCE_TO_COUNT_METERS) {
                double segmentoKmh = (segmentoMetros / 1000.0) / (segmentoMs / 3_600_000.0);
                if (segmentoKmh > 0.0 && segmentoKmh <= TaskWorker.MAX_REASONABLE_SPEED_KMH) {
                    velocidadMaxKmh = Math.max(velocidadMaxKmh, segmentoKmh);
                    if (i == ordenados.size() - 1 && velocidadFinalKmh <= 0.0) {
                        velocidadFinalKmh = segmentoKmh;
                    }
                }
            }
        }

        // Usa la distancia calculada por puntos y, si la web trae más distancia acumulada, no la pierde.
        double distanciaMetros = Math.max(distanciaGps, maxMetrosRecibidos);
        long tiempoSegundos = Math.max(0L, (fin - inicio) / 1000L);
        double velocidadMediaKmh = tiempoSegundos > 0
                ? (distanciaMetros / 1000.0) / (tiempoSegundos / 3600.0)
                : 0.0;

        if (velocidadMediaKmh > TaskWorker.MAX_REASONABLE_SPEED_KMH) {
            velocidadMediaKmh = 0.0;
        }

        return new ResumenViaje(
                userId,
                empresaId,
                sessionId,
                distanciaMetros,
                velocidadMediaKmh,
                velocidadMaxKmh,
                velocidadFinalKmh,
                tiempoSegundos,
                latFinal,
                lonFinal,
                fin,
                ordenados.size());
    }
}

class Punto {
    final double lat;
    final double lon;
    final double velocidadKmh;
    final long timestamp;

    Punto(double lat, double lon, double velocidadKmh, long timestamp) {
        this.lat = lat;
        this.lon = lon;
        this.velocidadKmh = velocidadKmh;
        this.timestamp = timestamp;
    }
}

class ResumenViaje {
    final int userId;
    final int empresaId;
    final String sessionId;
    final double distanciaMetros;
    final double velocidadMediaKmh;
    final double velocidadMaxKmh;
    final double velocidadFinalKmh;
    final long tiempoSegundos;
    final double latFinal;
    final double lonFinal;
    final long timestampFinal;
    final int numPuntos;

    ResumenViaje(int userId, int empresaId, String sessionId, double distanciaMetros,
                 double velocidadMediaKmh, double velocidadMaxKmh, double velocidadFinalKmh,
                 long tiempoSegundos, double latFinal, double lonFinal, long timestampFinal,
                 int numPuntos) {
        this.userId = userId;
        this.empresaId = empresaId;
        this.sessionId = sessionId;
        this.distanciaMetros = distanciaMetros;
        this.velocidadMediaKmh = velocidadMediaKmh;
        this.velocidadMaxKmh = velocidadMaxKmh;
        this.velocidadFinalKmh = velocidadFinalKmh;
        this.tiempoSegundos = tiempoSegundos;
        this.latFinal = latFinal;
        this.lonFinal = lonFinal;
        this.timestampFinal = timestampFinal;
        this.numPuntos = numPuntos;
    }
}
