package pbl6.arquitectura1.Worker;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Scanner;
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
        this.pool = Executors.newFixedThreadPool(4);
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

            channel.basicQos(4);
            channel.basicConsume(KafkaStreamConfig.QUEUE_TAREA, false, new MiConsumer(channel));

            System.out.println("[TaskWorker] Esperando tareas en Q:tarea... [TLS + DLX aktibo]");

            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            pool.shutdown();
            channel.close();

        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    public synchronized void parar() {
        notify();
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
        String[] p = mensaje.split(" ");

        if (p.length < 7) return;

        int userId = Integer.parseInt(p[0]);
        int empresaId = Integer.parseInt(p[1]);
        double lat = Double.parseDouble(p[2]);
        double lon = Double.parseDouble(p[3]);
        double velocidad = Double.parseDouble(p[4]);
        double metros = Double.parseDouble(p[5]);
        boolean terminado = Boolean.parseBoolean(p[6]);
        String sessionId = p.length >= 8 ? p[7] : "";
        long eventTimestamp = p.length >= 9 ? parseTimestamp(p[8]) : System.currentTimeMillis();

        String userKey = userId + ":" + sessionId;
        EstadoUsuario estado = usuarios.get(userKey);
        if (estado == null) {
            estado = new EstadoUsuario();
            estado.userId = userId;
            estado.empresaId = empresaId;
            estado.sessionId = sessionId;
            estado.timestampInicio = eventTimestamp;
            usuarios.put(userKey, estado);
        }

        if (sessionId != null && !sessionId.isBlank()) {
            estado.sessionId = sessionId;
        }
        estado.ultimaLat = lat;
        estado.ultimaLon = lon;
        estado.velocidad = velocidad;
        estado.metrosRecorridos = metros;
        estado.terminado = terminado;
        estado.ultimoTimestamp = eventTimestamp;

        if (estado.terminado) {
            long tiempoMs = estado.ultimoTimestamp - estado.timestampInicio;
            long tiempoSegundos = tiempoMs / 1000;
            String fecha = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new java.util.Date(estado.ultimoTimestamp));

            String linea = userId + " " + empresaId + " " + estado.metrosRecorridos + " " + estado.velocidad + " " + tiempoSegundos + " " + estado.ultimaLat + " " + estado.ultimaLon + " " + estado.ultimoTimestamp + " " + estado.sessionId;

            System.out.println("\n>>>>>>>>>>>>>>>>>>>>>>>" +
                    "\nUSER " + estado.userId + " HA TERMINADO SU RUTA" +
                    "\nEmpresa ID: " + estado.empresaId +
                    "\nDistancia total recorrida: " + String.format("%.2f", estado.metrosRecorridos) + " m" +
                    "\nVelocidad final: " + String.format("%.2f", estado.velocidad) + " km/h" +
                    "\n* Tiempo total: " + formatearTiempo(tiempoMs) +
                    "\n* Fecha llegada: " + fecha +
                    "\n>>>>>>>>>>>>>>>>>>>>>>>");

            channel.basicPublish(KafkaStreamConfig.EXCHANGE_FANOUT, "", null, linea.getBytes());
            usuarios.remove(userKey);
        }
        medidor.registrar(System.currentTimeMillis() - t0);
    }

    private long parseTimestamp(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return System.currentTimeMillis();
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
        System.out.println("Pulsa ENTER para parar.");
        TaskWorker worker = new TaskWorker();
        new Thread(() -> {
            Scanner teclado = new Scanner(System.in);
            teclado.nextLine();
            worker.parar();
            teclado.close();
        }).start();
        worker.suscribir();
    }
}

class EstadoUsuario {
    int userId; int empresaId; String sessionId = ""; double ultimaLat; double ultimaLon;
    double velocidad; double metrosRecorridos; boolean terminado;
    long ultimoTimestamp; long timestampInicio;
}