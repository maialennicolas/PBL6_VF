package pbl6.arquitectura2.Publisher;

import com.rabbitmq.client.*;

import pbl6.arquitectura2.Config.CO2StreamConfig;
import pbl6.arquitectura2.Config.TLSConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lainoa2 representa la capa final de la Arquitectura 2.
 *
 * Recibe los resultados ya calculados por WorkerCO2, los muestra por consola
 * y los persiste en MariaDB como auditoria del calculo ambiental.
 * Esto permite justificar una capa final tipo cloud/auditoria, util para
 * dashboards, trazabilidad y despliegue futuro.
 */
public class Lainoa2 {

    private static final String DB_URL = env("DB_URL", "");
    private static final String DB_USER = env("DB_USER", "ecomove");
    private static final String DB_PASSWORD = env("DB_PASSWORD", "ecomove");

    private final ConnectionFactory factory;
    private final AtomicLong auditRows = new AtomicLong(0);
    private final AtomicLong auditErrors = new AtomicLong(0);

    public Lainoa2() throws Exception {
        this.factory = TLSConfig.crearFactory();
        inicializarAuditoriaSiProcede();
    }

    public void suscribir() {
        try (com.rabbitmq.client.Connection connection = factory.newConnection()) {
            Channel channel = connection.createChannel();

            channel.exchangeDeclare(CO2StreamConfig.EXCHANGE_LAINOA2, "fanout", true);
            CO2StreamConfig.declareQueueWithDlx(channel,
                    CO2StreamConfig.QUEUE_LAINOA2_AUDITORIA,
                    CO2StreamConfig.QUEUE_DLQ_LAINOA2);
            channel.queueBind(CO2StreamConfig.QUEUE_LAINOA2_AUDITORIA, CO2StreamConfig.EXCHANGE_LAINOA2, "");
            channel.basicQos(8);
            channel.basicConsume(CO2StreamConfig.QUEUE_LAINOA2_AUDITORIA, false, new MiConsumer(channel));

            System.out.println("======================================================");
            System.out.println("[Lainoa2] Capa cloud/auditoria iniciada [TLS activo]");
            System.out.println("[Lainoa2] Esperando resultados CO2 en cola durable: " + CO2StreamConfig.QUEUE_LAINOA2_AUDITORIA);
            System.out.println("[Lainoa2] Auditoria BD: " + (dbConfigured() ? "activada" : "desactivada (DB_URL no configurado)"));
            System.out.println("======================================================");

            synchronized (this) {
                try { wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            channel.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void parar() { notify(); }

    public class MiConsumer extends DefaultConsumer {
        private static final SimpleDateFormat SDF = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

        public MiConsumer(Channel channel) { super(channel); }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope,
                AMQP.BasicProperties properties, byte[] body) throws IOException {

            String mensaje = new String(body, StandardCharsets.UTF_8);

            try {
                ResultadoAuditoria r = ResultadoAuditoria.fromMessage(mensaje);
                guardarAuditoria(r);
                imprimirResultado(r);

                getChannel().basicAck(envelope.getDeliveryTag(), false);
            } catch (Exception e) {
                auditErrors.incrementAndGet();
                System.err.println("[Lainoa2] Error procesando resultado CO2: " + e.getMessage());
                System.err.println("[Lainoa2] Mensaje original: " + mensaje);
                try {
                    getChannel().basicNack(envelope.getDeliveryTag(), false, false);
                } catch (Exception nackError) {
                    nackError.printStackTrace();
                }
            }
        }

        private void imprimirResultado(ResultadoAuditoria r) {
            String fecha  = SDF.format(new Date(r.eventTimestamp));
            String co2KG  = String.format(Locale.US, "%.3f", r.co2ConsumidoKg);
            String ahorKG = String.format(Locale.US, "%.3f", r.co2AhorradoKg);

            System.out.println();
            System.out.println("┌─ [Lainoa2] Resultado CO2 auditado ─────────────────┐");
            System.out.printf (Locale.US, "│  userId=%-4d  empresa=%-4d  Fecha: %s%n", r.userId, r.empresaId, fecha);
            System.out.printf (Locale.US, "│  Session      : %s%n", r.sessionId);
            System.out.printf (Locale.US, "│  Garraio mota : %-12s  Web: %-12s%n", r.sycdMode, r.webMode);
            System.out.printf (Locale.US, "│  Distancia    : %.2f km%n", r.km);
            System.out.printf (Locale.US, "│  CO2 emitido  : %s kg%n", co2KG);
            System.out.printf (Locale.US, "│  CO2 ahorrado : %s kg%n", ahorKG);
            System.out.printf (Locale.US, "│  Puntos       : %d%n", r.puntos);
            System.out.printf (Locale.US, "│  Carpool      : %s (%d persona/s)%n", r.carpool, r.personas);
            System.out.printf (Locale.US, "│  Koord.       : lat=%s  lon=%s%n", r.lat, r.lon);
            System.out.println("└────────────────────────────────────────────────────┘");
            System.out.printf(Locale.US,
                    "[Lainoa2][AUDIT] guardado=true filasAuditadas=%d errores=%d session=%s%n",
                    auditRows.get(), auditErrors.get(), r.sessionId);
        }
    }

    private void inicializarAuditoriaSiProcede() {
        if (!dbConfigured()) {
            return;
        }

        String sql = "CREATE TABLE IF NOT EXISTS co2_auditoria_ecomove ("
                + "auditID BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "userID BIGINT NOT NULL,"
                + "empresaID BIGINT NOT NULL,"
                + "sessionID VARCHAR(120) NOT NULL,"
                + "sycdMode VARCHAR(60),"
                + "webMode VARCHAR(60),"
                + "km DECIMAL(14,4) DEFAULT 0,"
                + "co2ConsumidoKg DECIMAL(14,4) DEFAULT 0,"
                + "co2AhorradoKg DECIMAL(14,4) DEFAULT 0,"
                + "puntos INT DEFAULT 0,"
                + "esCarpool TINYINT(1) DEFAULT 0,"
                + "numPasajeros INT DEFAULT 1,"
                + "latitud VARCHAR(40),"
                + "longitud VARCHAR(40),"
                + "eventTimestamp BIGINT,"
                + "fechaEvento DATETIME,"
                + "createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                + "updatedAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "UNIQUE KEY uq_co2_audit_user_session (userID, sessionID),"
                + "INDEX idx_co2_audit_empresa (empresaID),"
                + "INDEX idx_co2_audit_fecha (fechaEvento)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

        try (Connection cn = connection(); Statement st = cn.createStatement()) {
            st.execute(sql);
            System.out.println("[Lainoa2][AUDIT] Tabla co2_auditoria_ecomove lista.");
        } catch (SQLException e) {
            auditErrors.incrementAndGet();
            System.err.println("[Lainoa2][AUDIT] No se ha podido crear la tabla de auditoria: " + e.getMessage());
        }
    }

    private void guardarAuditoria(ResultadoAuditoria r) throws SQLException {
        if (!dbConfigured()) {
            return;
        }

        String sql = "INSERT INTO co2_auditoria_ecomove "
                + "(userID, empresaID, sessionID, sycdMode, webMode, km, co2ConsumidoKg, co2AhorradoKg, puntos, "
                + "esCarpool, numPasajeros, latitud, longitud, eventTimestamp, fechaEvento) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "empresaID=VALUES(empresaID), sycdMode=VALUES(sycdMode), webMode=VALUES(webMode), km=VALUES(km), "
                + "co2ConsumidoKg=VALUES(co2ConsumidoKg), co2AhorradoKg=VALUES(co2AhorradoKg), puntos=VALUES(puntos), "
                + "esCarpool=VALUES(esCarpool), numPasajeros=VALUES(numPasajeros), latitud=VALUES(latitud), "
                + "longitud=VALUES(longitud), eventTimestamp=VALUES(eventTimestamp), fechaEvento=VALUES(fechaEvento)";

        try (Connection cn = connection(); PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, r.userId);
            ps.setInt(2, r.empresaId);
            ps.setString(3, r.sessionId);
            ps.setString(4, r.sycdMode);
            ps.setString(5, r.webMode);
            ps.setDouble(6, r.km);
            ps.setDouble(7, r.co2ConsumidoKg);
            ps.setDouble(8, r.co2AhorradoKg);
            ps.setInt(9, r.puntos);
            ps.setBoolean(10, r.carpool);
            ps.setInt(11, r.personas);
            ps.setString(12, r.lat);
            ps.setString(13, r.lon);
            ps.setLong(14, r.eventTimestamp);
            ps.setTimestamp(15, new Timestamp(r.eventTimestamp));
            ps.executeUpdate();
            auditRows.incrementAndGet();
        }
    }

    private boolean dbConfigured() {
        return DB_URL != null && !DB_URL.isBlank();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static class ResultadoAuditoria {
        final int userId;
        final int empresaId;
        final String sycdMode;
        final double km;
        final double co2ConsumidoKg;
        final double co2AhorradoKg;
        final String lat;
        final String lon;
        final long eventTimestamp;
        final String sessionId;
        final String webMode;
        final int puntos;
        final boolean carpool;
        final int personas;

        ResultadoAuditoria(int userId, int empresaId, String sycdMode, double km,
                           double co2ConsumidoKg, double co2AhorradoKg, String lat, String lon,
                           long eventTimestamp, String sessionId, String webMode, int puntos,
                           boolean carpool, int personas) {
            this.userId = userId;
            this.empresaId = empresaId;
            this.sycdMode = sycdMode;
            this.km = km;
            this.co2ConsumidoKg = co2ConsumidoKg;
            this.co2AhorradoKg = co2AhorradoKg;
            this.lat = lat;
            this.lon = lon;
            this.eventTimestamp = eventTimestamp;
            this.sessionId = sessionId;
            this.webMode = webMode;
            this.puntos = puntos;
            this.carpool = carpool;
            this.personas = personas;
        }

        static ResultadoAuditoria fromMessage(String mensaje) {
            String[] p = mensaje.trim().split("\\s+");
            if (p.length < 9) {
                throw new IllegalArgumentException("Mensaje invalido (<9 tokens): " + mensaje);
            }

            int userId = Integer.parseInt(p[0]);
            int empresaId = Integer.parseInt(p[1]);
            String sycdMode = p[2];
            double km = parseDouble(p[3]);
            double co2Gramos = parseDouble(p[4]);
            double co2AhorradoGramos = parseDouble(p[5]);
            String lat = p[6];
            String lon = p[7];
            long ts = parseLongOrNow(p[8]);

            String sessionId = p.length >= 10 ? cleanToken(p[9]) : "NO_SESSION_" + userId + "_" + ts;
            String webMode = p.length >= 11 ? cleanToken(p[10]).replace('_', ' ') : sycdMode;
            int puntos = p.length >= 12 ? (int) parseDouble(p[11]) : 0;
            boolean carpool = p.length >= 13 && Boolean.parseBoolean(p[12]);
            int personas = p.length >= 14 ? Math.max(1, (int) parseDouble(p[13])) : 1;

            return new ResultadoAuditoria(
                    userId,
                    empresaId,
                    sycdMode,
                    km,
                    co2Gramos / 1000.0,
                    co2AhorradoGramos / 1000.0,
                    lat,
                    lon,
                    ts,
                    sessionId,
                    webMode,
                    puntos,
                    carpool,
                    personas);
        }

        private static String cleanToken(String value) {
            if (value == null || value.isBlank() || "-".equals(value)) {
                return "";
            }
            return value;
        }

        private static double parseDouble(String value) {
            if (value == null || value.isBlank()) {
                return 0.0;
            }
            return Double.parseDouble(value.replace(',', '.'));
        }

        private static long parseLongOrNow(String value) {
            try {
                return Long.parseLong(value);
            } catch (Exception ignored) {
                return System.currentTimeMillis();
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("[Lainoa2] Servicio iniciado.");
        try {
            Lainoa2 lainoa2 = new Lainoa2();
            lainoa2.suscribir();
        } catch (Exception e) {
            System.err.println("[Lainoa2] Error TLS/auditoria: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
