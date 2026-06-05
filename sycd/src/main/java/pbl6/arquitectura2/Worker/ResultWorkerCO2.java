package pbl6.arquitectura2.Worker;

import com.rabbitmq.client.*;

import pbl6.arquitectura2.Config.CO2StreamConfig;
import pbl6.arquitectura2.Config.TLSConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * ResultWorkerCO2 cumple dos funciones en la Arquitectura 2:
 *  1) recibe los resultados calculados por WorkerCO2 y los reenvia a Lainoa2.
 *  2) actua como servidor RPC de consultas SYCD para la web.
 *
 * Las consultas RPC usan RabbitMQ/TLS, correlationId y replyTo.
 */
public class ResultWorkerCO2 {

    public static final String QUEUE_CO2_CONSULTAS = "q.co2.consultas";

    private final ConnectionFactory factory;
    private final Map<Integer, EstadisticasEmpresa> estadisticas = new ConcurrentHashMap<>();

    private final AtomicInteger mensajesProcesados = new AtomicInteger(0);
    private final AtomicLong rpcRequests = new AtomicLong(0);
    private final AtomicLong rpcErrors = new AtomicLong(0);
    private final Instant startedAt = Instant.now();

    private static final int TOTAL_MENSAJES_ESPERADOS = 10;
    private final AtomicBoolean resumenImpreso = new AtomicBoolean(false);

    private static final String DB_URL = env("DB_URL", "");
    private static final String DB_USER = env("DB_USER", "ecomove");
    private static final String DB_PASSWORD = env("DB_PASSWORD", "ecomove");

    public ResultWorkerCO2() throws Exception {
        this.factory = TLSConfig.crearFactory();
    }

    public void suscribir() {
        try {
            com.rabbitmq.client.Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();

            channel.exchangeDeclare(CO2StreamConfig.EXCHANGE_CO2_RESULTADO, "direct", true);
            channel.exchangeDeclare(CO2StreamConfig.EXCHANGE_LAINOA2, "fanout", true);

            CO2StreamConfig.declareQueueWithDlx(channel, CO2StreamConfig.QUEUE_CO2_RESULTADO, CO2StreamConfig.QUEUE_DLQ_CO2_RESULTADO);
            channel.queueBind(CO2StreamConfig.QUEUE_CO2_RESULTADO,
                    CO2StreamConfig.EXCHANGE_CO2_RESULTADO,
                    CO2StreamConfig.QUEUE_CO2_RESULTADO);

            channel.basicQos(8);
            channel.basicConsume(CO2StreamConfig.QUEUE_CO2_RESULTADO, false, new MiConsumer(channel));

            CO2StreamConfig.declareQueueWithDlx(channel, QUEUE_CO2_CONSULTAS, CO2StreamConfig.QUEUE_DLQ_CO2_CONSULTAS);
            channel.basicConsume(QUEUE_CO2_CONSULTAS, false, new ServerRPCCO2Consumer(channel));

            System.out.println("[ResultWorkerCO2] Esperando resultados y consultas RPC en Q:" + QUEUE_CO2_CONSULTAS + " [TLS activo]");
            System.out.println("[ResultWorkerCO2] Consultas disponibles: STATUS, METRICS, USER <id>, EMPRESA <id>, EMPRESA_USER <empresaId>:<userId>");
            System.out.println("------------------------------------------------------");

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
        } catch (NumberFormatException e) {
            return ts;
        }
    }

    public class MiConsumer extends DefaultConsumer {
        public MiConsumer(Channel channel) { super(channel); }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope,
                AMQP.BasicProperties properties, byte[] body) throws IOException {

            String mensaje = new String(body, StandardCharsets.UTF_8);
            String[] p = mensaje.split(" ");
            if (p.length < 9) {
                getChannel().basicAck(envelope.getDeliveryTag(), false);
                return;
            }

            try {
                int userId = Integer.parseInt(p[0]);
                int empresaId = Integer.parseInt(p[1]);
                String mota = p[2];
                double distanciaKm = Double.parseDouble(p[3].replace(',', '.'));
                double co2Gramos = Double.parseDouble(p[4].replace(',', '.'));
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
            } catch (Exception e) {
                System.err.println("[ResultWorkerCO2] Error procesando resultado CO2: " + e.getMessage());
                getChannel().basicNack(envelope.getDeliveryTag(), false, false);
            }
        }
    }

    public class ServerRPCCO2Consumer extends DefaultConsumer {
        public ServerRPCCO2Consumer(Channel channel) { super(channel); }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope,
                AMQP.BasicProperties properties, byte[] body) throws IOException {

            rpcRequests.incrementAndGet();
            String corrId = properties.getCorrelationId() == null ? "" : properties.getCorrelationId();
            String replyTo = properties.getReplyTo();
            String peticion = new String(body, StandardCharsets.UTF_8).trim();
            String respuesta;

            try {
                respuesta = procesarConsulta(peticion);
            } catch (Exception e) {
                rpcErrors.incrementAndGet();
                respuesta = jsonError("Error procesando consulta SYCD: " + e.getMessage());
            }

            if (replyTo != null && !replyTo.isBlank()) {
                AMQP.BasicProperties replyProps = new AMQP.BasicProperties.Builder()
                        .correlationId(corrId)
                        .contentType("application/json")
                        .deliveryMode(1)
                        .build();
                getChannel().basicPublish("", replyTo, replyProps, respuesta.getBytes(StandardCharsets.UTF_8));
            }
            getChannel().basicAck(envelope.getDeliveryTag(), false);
        }
    }

    private String procesarConsulta(String peticion) throws Exception {
        if (peticion == null || peticion.isBlank()) {
            return jsonError("Consulta vacia. Usa STATUS, METRICS, USER <id>, EMPRESA <id> o EMPRESA_USER <empresaId>:<userId>.");
        }

        String[] partes = peticion.trim().split("\\s+", 2);
        String tipo = partes[0].toUpperCase(Locale.ROOT);
        String parametro = partes.length > 1 ? partes[1].trim() : "";

        return switch (tipo) {
            case "STATUS" -> statusJson();
            case "METRICS" -> metricsJson();
            case "USER", "USER_CO2" -> userJson(parseLongParam(parametro, "userId"));
            case "EMPRESA", "COMPANY", "COMPANY_CO2" -> empresaJson(parseLongParam(parametro, "empresaId"));
            case "EMPRESA_USER" -> empresaUserJson(parametro);
            default -> jsonError("Tipo de consulta no soportado: " + tipo);
        };
    }

    private long parseLongParam(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta parametro " + name);
        }
        return Long.parseLong(value.trim());
    }

    private String statusJson() {
        long uptimeSeconds = Duration.between(startedAt, Instant.now()).toSeconds();
        return "{"
                + kv("ok", true) + ","
                + kv("service", "SYCD Query Server") + ","
                + kv("architecture", "Arquitectura 2") + ","
                + kv("queue", QUEUE_CO2_CONSULTAS) + ","
                + kv("rabbitmq", "amqps/TLS") + ","
                + kv("dbConfigured", dbConfigured()) + ","
                + kv("uptimeSeconds", uptimeSeconds) + ","
                + kv("processedCo2Messages", mensajesProcesados.get()) + ","
                + kv("rpcRequests", rpcRequests.get()) + ","
                + kv("rpcErrors", rpcErrors.get()) + ","
                + kv("cachedCompanies", estadisticas.size())
                + "}";
    }

    private String metricsJson() throws SQLException {
        long uptimeSeconds = Duration.between(startedAt, Instant.now()).toSeconds();
        DbTotals totals = dbConfigured() ? queryDbTotals() : new DbTotals(0, 0, 0, 0, 0, 0, 0);
        return "{"
                + kv("ok", true) + ","
                + kv("type", "METRICS") + ","
                + kv("uptimeSeconds", uptimeSeconds) + ","
                + kv("processedCo2Messages", mensajesProcesados.get()) + ","
                + kv("rpcRequests", rpcRequests.get()) + ","
                + kv("rpcErrors", rpcErrors.get()) + ","
                + kv("dbTrips", totals.trips) + ","
                + kv("dbUsers", totals.users) + ","
                + kv("dbKm", totals.km) + ","
                + kv("dbCo2ConsumidoKg", totals.consumed) + ","
                + kv("dbCo2AhorradoKg", totals.saved) + ","
                + kv("dbPuntos", totals.points) + ","
                + kv("dbCarpoolTrips", totals.carpoolTrips) + ","
                + kv("dbAuditRows", dbConfigured() ? auditRowsCount() : 0)
                + "}";
    }

    private String userJson(long userId) throws SQLException {
        if (!dbConfigured()) {
            return userJsonFromMemory(userId);
        }

        try (Connection cn = connection()) {
            UserInfo user = findUser(cn, userId);
            TripTotals totals = tripTotals(cn, "WHERE CAST(NULLIF(`userID`, '') AS UNSIGNED) = ?", userId);
            String modes = modesJson(cn, "WHERE CAST(NULLIF(`userID`, '') AS UNSIGNED) = ?", userId);
            String latest = latestTripsJson(cn, "WHERE CAST(NULLIF(`userID`, '') AS UNSIGNED) = ?", userId, 5);

            return "{"
                    + kv("ok", true) + ","
                    + kv("source", "SYCD-Arquitectura2-RPC-BD") + ","
                    + kv("type", "USER") + ","
                    + kv("userId", userId) + ","
                    + kv("nombre", user.name) + ","
                    + kv("empresaId", user.empresaId) + ","
                    + kv("totalTrips", totals.trips) + ","
                    + kv("totalKm", totals.km) + ","
                    + kv("co2ConsumidoKg", totals.consumed) + ","
                    + kv("co2AhorradoKg", totals.saved) + ","
                    + kv("puntos", totals.points) + ","
                    + kv("carpoolTrips", totals.carpoolTrips) + ","
                    + "\"byMode\":" + modes + ","
                    + "\"latestTrips\":" + latest
                    + "}";
        }
    }

    private String empresaJson(long empresaId) throws SQLException {
        if (!dbConfigured()) {
            return empresaJsonFromMemory((int) empresaId);
        }

        try (Connection cn = connection()) {
            String companyName = findCompanyName(cn, empresaId);
            String where = "WHERE CAST(NULLIF(t.`userID`, '') AS UNSIGNED) IN (SELECT u.userid FROM usuarios_ecomove u WHERE u.empresaid = ?)";
            TripTotals totals = tripTotalsJoin(cn, where, empresaId);
            String modes = modesJsonJoin(cn, where, empresaId);
            long activeUsers = activeUsersForCompany(cn, empresaId);

            return "{"
                    + kv("ok", true) + ","
                    + kv("source", "SYCD-Arquitectura2-RPC-BD") + ","
                    + kv("type", "EMPRESA") + ","
                    + kv("empresaId", empresaId) + ","
                    + kv("nombre", companyName) + ","
                    + kv("activeUsers", activeUsers) + ","
                    + kv("totalTrips", totals.trips) + ","
                    + kv("totalKm", totals.km) + ","
                    + kv("co2ConsumidoKg", totals.consumed) + ","
                    + kv("co2AhorradoKg", totals.saved) + ","
                    + kv("puntos", totals.points) + ","
                    + kv("carpoolTrips", totals.carpoolTrips) + ","
                    + "\"byMode\":" + modes
                    + "}";
        }
    }

    private String empresaUserJson(String parametro) throws SQLException {
        String[] ids = parametro.split(":");
        if (ids.length != 2) {
            return jsonError("EMPRESA_USER necesita formato empresaId:userId");
        }
        long empresaId = Long.parseLong(ids[0].trim());
        long userId = Long.parseLong(ids[1].trim());

        if (!dbConfigured()) {
            return buscarUsuarioEnEmpresaMemory((int) empresaId, (int) userId);
        }

        try (Connection cn = connection()) {
            UserInfo user = findUser(cn, userId);
            if (user.empresaId != empresaId) {
                return jsonError("El usuario " + userId + " no pertenece a la empresa " + empresaId);
            }
            return userJson(userId);
        }
    }

    private String userJsonFromMemory(long userId) {
        for (EstadisticasEmpresa emp : estadisticas.values()) {
            DatosUsuario u = emp.usuarios.get((int) userId);
            if (u != null) {
                return "{"
                        + kv("ok", true) + ","
                        + kv("source", "SYCD-Arquitectura2-RPC-MEMORY") + ","
                        + kv("type", "USER") + ","
                        + kv("empresaId", emp.empresaId) + ","
                        + kv("userId", u.userId) + ","
                        + kv("mode", u.mota) + ","
                        + kv("totalKm", u.distanciaKm) + ","
                        + kv("co2ConsumidoKg", u.co2Gramos / 1000.0) + ","
                        + kv("co2AhorradoKg", u.co2Ahorrado / 1000.0) + ","
                        + kv("timestamp", formatearTimestamp(u.timestamp))
                        + "}";
            }
        }
        return jsonError("El usuario " + userId + " no tiene datos en memoria.");
    }

    private String empresaJsonFromMemory(int empresaId) {
        EstadisticasEmpresa emp = estadisticas.get(empresaId);
        if (emp == null || emp.usuarios.isEmpty()) return jsonError("La empresa " + empresaId + " no tiene datos en memoria.");
        double media = emp.co2Total.sum() / Math.max(1, emp.usuarios.size());
        return "{"
                + kv("ok", true) + ","
                + kv("source", "SYCD-Arquitectura2-RPC-MEMORY") + ","
                + kv("type", "EMPRESA") + ","
                + kv("empresaId", emp.empresaId) + ","
                + kv("activeUsers", emp.usuarios.size()) + ","
                + kv("co2MediaGramos", media) + ","
                + kv("co2TotalGramos", emp.co2Total.sum()) + ","
                + kv("co2AhorradoTotalGramos", emp.co2AhorradoTotal.sum())
                + "}";
    }

    private String buscarUsuarioEnEmpresaMemory(int empresaId, int userId) {
        EstadisticasEmpresa emp = estadisticas.get(empresaId);
        if (emp == null) return jsonError("La empresa " + empresaId + " no existe en memoria.");
        DatosUsuario u = emp.usuarios.get(userId);
        if (u == null) return jsonError("El usuario " + userId + " no esta en la empresa " + empresaId + ".");
        return userJsonFromMemory(userId);
    }

    private void enviarALainoa2(String mensaje) throws Exception {
        try (com.rabbitmq.client.Connection conn = factory.newConnection();
             Channel ch = conn.createChannel()) {
            ch.exchangeDeclare(CO2StreamConfig.EXCHANGE_LAINOA2, "fanout", true);
            CO2StreamConfig.declareQueueWithDlx(ch,
                    CO2StreamConfig.QUEUE_LAINOA2_AUDITORIA,
                    CO2StreamConfig.QUEUE_DLQ_LAINOA2);
            ch.queueBind(CO2StreamConfig.QUEUE_LAINOA2_AUDITORIA, CO2StreamConfig.EXCHANGE_LAINOA2, "");
            ch.basicPublish(CO2StreamConfig.EXCHANGE_LAINOA2, "", CO2StreamConfig.persistentTextProperties(), mensaje.getBytes(StandardCharsets.UTF_8));
        }
    }

    private synchronized void imprimirResumenActual() {
        System.out.println();
        List<EstadisticasEmpresa> empresas = new ArrayList<>(estadisticas.values());
        empresas.sort(Comparator.comparingInt(e -> e.empresaId));
        for (EstadisticasEmpresa empresa : empresas) {
            double media = empresa.co2Total.sum() / Math.max(1, empresa.usuarios.size());
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
        System.out.println("\n------------------------------------------------------");
        imprimirResumenActual();
        System.out.println("------------------------------------------------------");
    }

    private boolean dbConfigured() {
        return DB_URL != null && !DB_URL.isBlank();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    private UserInfo findUser(Connection cn, long userId) throws SQLException {
        String sql = "SELECT userid, empresaid, nombre, apellidos, nombre_usuario FROM usuarios_ecomove WHERE userid = ?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = (rs.getString("nombre") == null ? "" : rs.getString("nombre")) + " "
                            + (rs.getString("apellidos") == null ? "" : rs.getString("apellidos"));
                    return new UserInfo(rs.getLong("userid"), rs.getLong("empresaid"), name.trim(), rs.getString("nombre_usuario"));
                }
            }
        }
        return new UserInfo(userId, 0, "", "");
    }

    private String findCompanyName(Connection cn, long empresaId) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement("SELECT `nombre` FROM empresas_ecomove WHERE CAST(NULLIF(`empresaID`, '') AS UNSIGNED) = ? LIMIT 1")) {
            ps.setLong(1, empresaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1) == null ? "" : rs.getString(1);
            }
        } catch (SQLException ignored) {
            // La tabla puede no existir en proyectos parcialmente migrados.
        }
        return "";
    }

    private long activeUsersForCompany(Connection cn, long empresaId) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement("SELECT COUNT(*) FROM usuarios_ecomove WHERE empresaid = ?")) {
            ps.setLong(1, empresaId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private TripTotals tripTotals(Connection cn, String where, long param) throws SQLException {
        String sql = "SELECT COUNT(*), "
                + "COALESCE(SUM(CAST(COALESCE(NULLIF(`km`, ''), '0') AS DECIMAL(14,4))),0), "
                + "COALESCE(SUM(CAST(COALESCE(NULLIF(`co2ConsumidoKg`, ''), '0') AS DECIMAL(14,4))),0), "
                + "COALESCE(SUM(CAST(COALESCE(NULLIF(`co2AhorradoKg`, ''), '0') AS DECIMAL(14,4))),0), "
                + "COALESCE(SUM(CAST(COALESCE(NULLIF(`puntos`, ''), '0') AS DECIMAL(14,4))),0), "
                + "SUM(CASE WHEN LOWER(COALESCE(`esCarpool`, 'false')) = 'true' THEN 1 ELSE 0 END) "
                + "FROM viajes_ecomove " + where;
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            if (where != null && where.contains("?")) {
                ps.setLong(1, param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return readTripTotals(rs);
            }
        }
    }

    private TripTotals tripTotalsJoin(Connection cn, String where, long param) throws SQLException {
        String sql = "SELECT COUNT(*), "
                + "COALESCE(SUM(CAST(COALESCE(NULLIF(t.`km`, ''), '0') AS DECIMAL(14,4))),0), "
                + "COALESCE(SUM(CAST(COALESCE(NULLIF(t.`co2ConsumidoKg`, ''), '0') AS DECIMAL(14,4))),0), "
                + "COALESCE(SUM(CAST(COALESCE(NULLIF(t.`co2AhorradoKg`, ''), '0') AS DECIMAL(14,4))),0), "
                + "COALESCE(SUM(CAST(COALESCE(NULLIF(t.`puntos`, ''), '0') AS DECIMAL(14,4))),0), "
                + "SUM(CASE WHEN LOWER(COALESCE(t.`esCarpool`, 'false')) = 'true' THEN 1 ELSE 0 END) "
                + "FROM viajes_ecomove t " + where;
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            if (where != null && where.contains("?")) {
                ps.setLong(1, param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return readTripTotals(rs);
            }
        }
    }

    private TripTotals readTripTotals(ResultSet rs) throws SQLException {
        if (rs.next()) {
            return new TripTotals(rs.getLong(1), rs.getDouble(2), rs.getDouble(3), rs.getDouble(4), rs.getLong(5), rs.getLong(6));
        }
        return new TripTotals(0, 0, 0, 0, 0, 0);
    }

    private String modesJson(Connection cn, String where, long param) throws SQLException {
        String sql = "SELECT COALESCE(NULLIF(`modo`, ''), 'SIN_CALCULAR') AS mode, COUNT(*), "
                + "COALESCE(SUM(CAST(COALESCE(NULLIF(`km`, ''), '0') AS DECIMAL(14,4))),0), "
                + "COALESCE(SUM(CAST(COALESCE(NULLIF(`co2AhorradoKg`, ''), '0') AS DECIMAL(14,4))),0) "
                + "FROM viajes_ecomove " + where + " GROUP BY COALESCE(NULLIF(`modo`, ''), 'SIN_CALCULAR')";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return modeRowsJson(rs);
            }
        }
    }

    private String modesJsonJoin(Connection cn, String where, long param) throws SQLException {
        String sql = "SELECT COALESCE(NULLIF(t.`modo`, ''), 'SIN_CALCULAR') AS mode, COUNT(*), "
                + "COALESCE(SUM(CAST(COALESCE(NULLIF(t.`km`, ''), '0') AS DECIMAL(14,4))),0), "
                + "COALESCE(SUM(CAST(COALESCE(NULLIF(t.`co2AhorradoKg`, ''), '0') AS DECIMAL(14,4))),0) "
                + "FROM viajes_ecomove t " + where + " GROUP BY COALESCE(NULLIF(t.`modo`, ''), 'SIN_CALCULAR')";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return modeRowsJson(rs);
            }
        }
    }

    private String modeRowsJson(ResultSet rs) throws SQLException {
        StringBuilder out = new StringBuilder("[");
        boolean first = true;
        while (rs.next()) {
            if (!first) out.append(',');
            first = false;
            out.append('{')
                    .append(kv("mode", rs.getString(1))).append(',')
                    .append(kv("trips", rs.getLong(2))).append(',')
                    .append(kv("km", rs.getDouble(3))).append(',')
                    .append(kv("co2AhorradoKg", rs.getDouble(4)))
                    .append('}');
        }
        out.append(']');
        return out.toString();
    }

    private String latestTripsJson(Connection cn, String where, long param, int limit) throws SQLException {
        String sql = "SELECT `tripID`, `sessionID`, `modo`, `km`, `co2ConsumidoKg`, `co2AhorradoKg`, `puntos`, `fecha`, `esCarpool`, `rolCarpool` "
                + "FROM viajes_ecomove " + where + " ORDER BY CAST(COALESCE(NULLIF(`tripID`, ''), '0') AS UNSIGNED) DESC LIMIT ?";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setLong(1, param);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                StringBuilder out = new StringBuilder("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) out.append(',');
                    first = false;
                    out.append('{')
                            .append(kv("tripId", rs.getString(1))).append(',')
                            .append(kv("sessionId", rs.getString(2))).append(',')
                            .append(kv("mode", rs.getString(3))).append(',')
                            .append(kv("km", num(rs.getString(4)))).append(',')
                            .append(kv("co2ConsumidoKg", num(rs.getString(5)))).append(',')
                            .append(kv("co2AhorradoKg", num(rs.getString(6)))).append(',')
                            .append(kv("puntos", (long) num(rs.getString(7)))).append(',')
                            .append(kv("fecha", rs.getString(8))).append(',')
                            .append(kv("carpool", "true".equalsIgnoreCase(rs.getString(9)))).append(',')
                            .append(kv("rolCarpool", rs.getString(10)))
                            .append('}');
                }
                out.append(']');
                return out.toString();
            }
        }
    }

    private long auditRowsCount() {
        if (!dbConfigured()) {
            return 0;
        }
        try (Connection cn = connection();
             Statement st = cn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM co2_auditoria_ecomove")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException ignored) {
            return 0;
        }
    }

    private DbTotals queryDbTotals() throws SQLException {
        try (Connection cn = connection()) {
            long users = 0;
            try (Statement st = cn.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM usuarios_ecomove")) {
                if (rs.next()) users = rs.getLong(1);
            }
            TripTotals t = tripTotals(cn, "", 0);
            return new DbTotals(users, t.trips, t.km, t.consumed, t.saved, t.points, t.carpoolTrips);
        }
    }

    private static double num(String value) {
        if (value == null || value.isBlank()) return 0.0;
        try { return Double.parseDouble(value.replace(',', '.')); }
        catch (Exception ignored) { return 0.0; }
    }

    private static String jsonError(String message) {
        return "{" + kv("ok", false) + "," + kv("error", message) + "}";
    }

    private static String kv(String key, String value) {
        return "\"" + escape(key) + "\":\"" + escape(value == null ? "" : value) + "\"";
    }

    private static String kv(String key, boolean value) {
        return "\"" + escape(key) + "\":" + value;
    }

    private static String kv(String key, long value) {
        return "\"" + escape(key) + "\":" + value;
    }

    private static String kv(String key, double value) {
        return "\"" + escape(key) + "\":" + String.format(Locale.US, "%.4f", value);
    }

    private static String escape(String value) {
        return String.valueOf(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    static class DatosUsuario {
        final int userId;
        final String mota;
        final double distanciaKm;
        final double co2Gramos;
        final double co2Ahorrado;
        final String lat, lon, timestamp;

        DatosUsuario(int userId, String mota, double distanciaKm,
                     double co2Gramos, double co2Ahorrado,
                     String lat, String lon, String timestamp) {
            this.userId = userId;
            this.mota = mota;
            this.distanciaKm = distanciaKm;
            this.co2Gramos = co2Gramos;
            this.co2Ahorrado = co2Ahorrado;
            this.lat = lat;
            this.lon = lon;
            this.timestamp = timestamp;
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
            if (anterior != null) {
                co2Total.add(-anterior.co2Gramos);
                co2AhorradoTotal.add(-anterior.co2Ahorrado);
            }
            usuarios.put(userId, new DatosUsuario(userId, mota, distKm, co2g, ahorradoG, lat, lon, timestamp));
            co2Total.add(co2g);
            co2AhorradoTotal.add(ahorradoG);
        }
    }

    private record UserInfo(long userId, long empresaId, String name, String username) {}
    private record TripTotals(long trips, double km, double consumed, double saved, long points, long carpoolTrips) {}
    private record DbTotals(long users, long trips, double km, double consumed, double saved, long points, long carpoolTrips) {}

    public static void main(String[] args) {
        try {
            new ResultWorkerCO2().suscribir();
        } catch (Exception e) {
            System.err.println("[ResultWorkerCO2] Error TLS/RPC: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
