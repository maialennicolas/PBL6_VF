package pbl6.arquitectura2.Worker;

import com.rabbitmq.client.*;

import pbl6.arquitectura2.Config.CO2StreamConfig;
import pbl6.arquitectura2.Config.TLSConfig;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;

public class ResultWorkerCO2 {

    private final ConnectionFactory factory;
    private final Map<Integer, EstadisticasEmpresa> estadisticas = new ConcurrentHashMap<>();

    private final AtomicInteger mensajesProcesados  = new AtomicInteger(0);
    private static final int    TOTAL_MENSAJES_ESPERADOS = 10;
    private final AtomicBoolean resumenImpreso      = new AtomicBoolean(false);

    public ResultWorkerCO2() throws Exception {
        this.factory = TLSConfig.crearFactory();   // ← TLS
    }

    public void suscribir() {
        try {
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();

            channel.exchangeDeclare(CO2StreamConfig.EXCHANGE_CO2_RESULTADO, "direct", true);
            channel.exchangeDeclare(CO2StreamConfig.EXCHANGE_LAINOA2, "fanout", true);

            channel.queueDeclare(CO2StreamConfig.QUEUE_CO2_RESULTADO, true, false, false, null);
            channel.queueBind(CO2StreamConfig.QUEUE_CO2_RESULTADO,
                    CO2StreamConfig.EXCHANGE_CO2_RESULTADO,
                    CO2StreamConfig.QUEUE_CO2_RESULTADO);

            channel.basicQos(1);
            channel.basicConsume(CO2StreamConfig.QUEUE_CO2_RESULTADO, false, new MiConsumer(channel));

            String colaConsultas = "q.co2.consultas";
            channel.queueDeclare(colaConsultas, true, false, false, null);
            channel.basicConsume(colaConsultas, false, new ServerRPCCO2Consumer(channel));

            System.out.println("[ResultWorkerCO2] Esperando resultados y listo para consultas RPC... [TLS aktibo]");
            System.out.println("──────────────────────────────────────────────────────");

            Thread.currentThread().join();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String formatearTimestamp(String ts) {
        try {
            long ms = Long.parseLong(ts);
            LocalDateTime fecha = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
            return fecha.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (NumberFormatException e) { return ts; }
    }

    public class MiConsumer extends DefaultConsumer {
        public MiConsumer(Channel channel) { super(channel); }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope,
                AMQP.BasicProperties properties, byte[] body) throws IOException {

            String mensaje = new String(body, "UTF-8");
            String[] p = mensaje.split(" ");
            if (p.length < 9) { getChannel().basicAck(envelope.getDeliveryTag(), false); return; }

            int    userId      = Integer.parseInt(p[0]);
            int    empresaId   = Integer.parseInt(p[1]);
            String mota        = p[2];
            double distanciaKm = Double.parseDouble(p[3].replace(',', '.'));
            double co2Gramos   = Double.parseDouble(p[4].replace(',', '.'));
            double co2Ahorrado = Double.parseDouble(p[5].replace(',', '.'));
            String lat = p[6], lon = p[7], timestamp = p[8];

            estadisticas
                .computeIfAbsent(empresaId, EstadisticasEmpresa::new)
                .agregarUsuario(userId, mota, distanciaKm, co2Gramos, co2Ahorrado, lat, lon, timestamp);

            enviarALainoa2(mensaje);
            getChannel().basicAck(envelope.getDeliveryTag(), false);

            if (mensajesProcesados.incrementAndGet() >= TOTAL_MENSAJES_ESPERADOS) {
                if (resumenImpreso.compareAndSet(false, true)) {
                    imprimirResumenFinal();
                }
            }
        }
    }

    public class ServerRPCCO2Consumer extends DefaultConsumer {
        public ServerRPCCO2Consumer(Channel channel) { super(channel); }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope,
                AMQP.BasicProperties properties, byte[] body) throws IOException {

            AMQP.BasicProperties replyProps = new AMQP.BasicProperties.Builder()
                    .correlationId(properties.getCorrelationId()).build();

            String peticion = new String(body, "UTF-8");
            String[] partes = peticion.split(" ");
            String respuesta = "No se encontraron datos.";

            if (partes.length >= 2) {
                String tipo = partes[0], parametro = partes[1];
                try {
                    if (tipo.equals("USER")) {
                        respuesta = buscarUsuarioGlobal(Integer.parseInt(parametro));
                    } else if (tipo.equals("EMPRESA")) {
                        respuesta = obtenerDatosEmpresa(Integer.parseInt(parametro));
                    } else if (tipo.equals("EMPRESA_USER")) {
                        String[] ids = parametro.split(":");
                        respuesta = buscarUsuarioEnEmpresa(Integer.parseInt(ids[0]), Integer.parseInt(ids[1]));
                    }
                } catch (Exception e) { respuesta = "Error al procesar los parámetros."; }
            }

            getChannel().basicPublish("", properties.getReplyTo(), replyProps, respuesta.getBytes("UTF-8"));
            getChannel().basicAck(envelope.getDeliveryTag(), false);
        }

        private String buscarUsuarioGlobal(int userId) {
            for (EstadisticasEmpresa emp : estadisticas.values()) {
                DatosUsuario u = emp.usuarios.get(userId);
                if (u != null) return String.format(Locale.US,
                        "empresa %d -> user %d | MODALIDAD: %s | DISTANCIA: %.2f km | CO2: %.1f g | AHORRO: %.1f g | FECHA: %s",
                        emp.empresaId, u.userId, u.mota, u.distanciaKm, u.co2Gramos, u.co2Ahorrado,
                        formatearTimestamp(u.timestamp));
            }
            return "El usuario " + userId + " no existe.";
        }

        private String obtenerDatosEmpresa(int empresaId) {
            EstadisticasEmpresa emp = estadisticas.get(empresaId);
            if (emp == null || emp.usuarios.isEmpty()) return "La empresa " + empresaId + " no tiene datos.";
            double media = emp.co2Total.sum() / emp.usuarios.size();
            return String.format(Locale.US, "empresa %d: (media %.1f) | %d usuarios registrados.",
                    emp.empresaId, media, emp.usuarios.size());
        }

        private String buscarUsuarioEnEmpresa(int empresaId, int userId) {
            EstadisticasEmpresa emp = estadisticas.get(empresaId);
            if (emp == null) return "La empresa " + empresaId + " no existe.";
            DatosUsuario u = emp.usuarios.get(userId);
            if (u == null) return "El usuario " + userId + " no está en la empresa " + empresaId + ".";
            return String.format(Locale.US,
                    "empresa %d -> user %d | MODALIDAD: %s | DISTANCIA: %.2f km | CO2: %.1f g | AHORRO: %.1f g | FECHA: %s",
                    emp.empresaId, u.userId, u.mota, u.distanciaKm, u.co2Gramos, u.co2Ahorrado,
                    formatearTimestamp(u.timestamp));
        }
    }

    private void enviarALainoa2(String mensaje) {
        try (Connection conn = factory.newConnection();
             Channel ch = conn.createChannel()) {
            ch.exchangeDeclare(CO2StreamConfig.EXCHANGE_LAINOA2, "fanout", true);
            ch.basicPublish(CO2StreamConfig.EXCHANGE_LAINOA2, "", null, mensaje.getBytes());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private synchronized void imprimirResumenActual() {
        System.out.println();
        List<EstadisticasEmpresa> empresas = new ArrayList<>(estadisticas.values());
        empresas.sort(Comparator.comparingInt(e -> e.empresaId));
        for (EstadisticasEmpresa empresa : empresas) {
            double media = empresa.co2Total.sum() / empresa.usuarios.size();
            System.out.printf(Locale.US, "empresa %d: (media %.1f)%n", empresa.empresaId, media);
            List<DatosUsuario> usuarios = new ArrayList<>(empresa.usuarios.values());
            usuarios.sort(Comparator.comparingInt(u -> u.userId));
            for (DatosUsuario u : usuarios) {
                System.out.printf(Locale.US,
                        "  user %d -> MODALIDAD: %s | DISTANCIA: %.2f km | CO2: %.1f g | AHORRO: %.1f g | COORDENADAS: [%s, %s] | FECHA: %s%n",
                        u.userId, u.mota, u.distanciaKm, u.co2Gramos, u.co2Ahorrado, u.lat, u.lon,
                        formatearTimestamp(u.timestamp));
            }
            System.out.println();
        }
    }

    private void imprimirResumenFinal() {
        System.out.println("\n──────────────────────────────────────────────────────");
        imprimirResumenActual();
        System.out.println("──────────────────────────────────────────────────────");
    }

    static class DatosUsuario {
        final int userId; final String mota; final double distanciaKm;
        final double co2Gramos; final double co2Ahorrado;
        final String lat, lon, timestamp;
        DatosUsuario(int userId, String mota, double distanciaKm,
                     double co2Gramos, double co2Ahorrado,
                     String lat, String lon, String timestamp) {
            this.userId=userId; this.mota=mota; this.distanciaKm=distanciaKm;
            this.co2Gramos=co2Gramos; this.co2Ahorrado=co2Ahorrado;
            this.lat=lat; this.lon=lon; this.timestamp=timestamp;
        }
    }

    static class EstadisticasEmpresa {
        final int empresaId;
        final Map<Integer, DatosUsuario> usuarios = new ConcurrentHashMap<>();
        final DoubleAdder co2Total = new DoubleAdder();
        final DoubleAdder co2AhorradoTotal = new DoubleAdder();
        EstadisticasEmpresa(int empresaId) { this.empresaId = empresaId; }
        synchronized void agregarUsuario(int userId, String mota, double distKm,
                double co2g, double ahorradoG, String lat, String lon, String timestamp) {
            DatosUsuario anterior = usuarios.get(userId);
            if (anterior != null) { co2Total.add(-anterior.co2Gramos); co2AhorradoTotal.add(-anterior.co2Ahorrado); }
            usuarios.put(userId, new DatosUsuario(userId, mota, distKm, co2g, ahorradoG, lat, lon, timestamp));
            co2Total.add(co2g); co2AhorradoTotal.add(ahorradoG);
        }
    }

    public static void main(String[] args) {
        try {
            new ResultWorkerCO2().suscribir();
        } catch (Exception e) {
            System.err.println("[ResultWorkerCO2] Error TLS: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
