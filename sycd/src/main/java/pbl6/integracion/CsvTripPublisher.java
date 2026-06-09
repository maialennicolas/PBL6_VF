package pbl6.integracion;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import pbl6.arquitectura1.Publisher.KafkaStreamConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Puente WEB -> SYCD.
 * Lee los viajes PENDIENTE/REINTENTAR_SYCD de data/viajes.csv, toma las ubicaciones
 * de data/ubicaciones_bidaia.csv y las publica a RabbitMQ con el formato que espera TaskWorker.
 */
public class CsvTripPublisher {
    private static final String TRIPS_FILE = "viajes.csv";
    private static final String LOCATIONS_FILE = "ubicaciones_bidaia.csv";
    private static final String USERS_FILE = "usuarios.csv";
    private static final long POLL_MS = Long.parseLong(System.getenv().getOrDefault("SYCD_CSV_POLL_MS", "2000"));
    private static final long EVENT_DELAY_MS = Long.parseLong(System.getenv().getOrDefault("SYCD_EVENT_DELAY_MS", "60"));

    private final Path dataDir = CsvUtil.dataDir();
    private final Set<String> processingSessions = ConcurrentHashMap.newKeySet();
    private final ConnectionFactory factory;

    public CsvTripPublisher() throws Exception {
        this.factory = pbl6.arquitectura1.Config.TLSConfig.crearFactory();
    }

    public void runForever() {
        System.out.println("[CsvTripPublisher] Escuchando CSV en " + dataDir.toAbsolutePath());
        while (true) {
            try {
                processPendingTrips();
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                System.err.println("[CsvTripPublisher] Error procesando CSV: " + e.getMessage());
                e.printStackTrace();
                sleepQuietly(POLL_MS);
            }
        }
    }

    private void processPendingTrips() throws Exception {
        List<String> headers = CsvUtil.readHeaders(dataDir, TRIPS_FILE);
        if (headers.isEmpty()) return;

        List<Map<String, String>> trips = new ArrayList<>(CsvUtil.readRows(dataDir, TRIPS_FILE));
        boolean changed = false;

        for (Map<String, String> trip : trips) {
            String status = trip.getOrDefault("estadoCalculo", "");
            String sessionId = trip.getOrDefault("sessionID", "");
            if (sessionId.isBlank()) continue;
            if (!(status.equalsIgnoreCase("PENDIENTE") || status.equalsIgnoreCase("REINTENTAR_SYCD"))) continue;
            if (!processingSessions.add(sessionId)) continue;

            trip.put("estadoCalculo", "PROCESANDO_SYCD");
            changed = true;
            new Thread(() -> publishTrip(sessionId), "csv-trip-" + sessionId).start();
        }

        if (changed) {
            CsvUtil.writeRows(dataDir, TRIPS_FILE, headers, trips);
        }
    }

    private void publishTrip(String sessionId) {
        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(KafkaStreamConfig.EXCHANGE_STREAM, "direct", true);

            Map<String, String> trip = findTrip(sessionId);
            if (trip == null) return;

            long userId = CsvUtil.parseLong(trip.get("userID"));
            long empresaId = findEmpresaId(userId);
            List<Map<String, String>> locations = locationsFor(userId, sessionId, trip);
            if (locations.isEmpty()) {
                markRetry(sessionId, "SIN_UBICACIONES");
                return;
            }

            System.out.println("[CsvTripPublisher] Enviando viaje session=" + sessionId + " user=" + userId + " puntos=" + locations.size());

            double cumulativeMeters = 0.0;
            Map<String, String> previous = null;
            for (int i = 0; i < locations.size(); i++) {
                Map<String, String> location = locations.get(i);
                double lat = CsvUtil.parseDouble(location.get("latitud"));
                double lon = CsvUtil.parseDouble(location.get("longitud"));

                double stepMeters = 0.0;
                if (previous != null) {
                    stepMeters = haversineMeters(
                            CsvUtil.parseDouble(previous.get("latitud")),
                            CsvUtil.parseDouble(previous.get("longitud")),
                            lat,
                            lon);
                }
                cumulativeMeters += stepMeters;

                double speedKmh = CsvUtil.parseDouble(location.get("speed"));
                if (speedKmh > 0 && speedKmh < 3.0) speedKmh *= 3.6; // algunos navegadores envían m/s
                if (speedKmh <= 0 && previous != null) {
                    long seconds = secondsBetween(previous.get("timestamp"), location.get("timestamp"));
                    if (seconds > 0) speedKmh = (stepMeters / seconds) * 3.6;
                }
                if (speedKmh <= 0) speedKmh = averageSpeedFromTrip(trip);

                boolean finished = i == locations.size() - 1;
                String message = String.format(Locale.US, "%d %d %.6f %.6f %.2f %.2f %b %s",
                        userId, empresaId, lat, lon, speedKmh, cumulativeMeters, finished, sessionId);

                channel.basicPublish(
                        KafkaStreamConfig.EXCHANGE_STREAM,
                        KafkaStreamConfig.QUEUE_TAREA,
                        null,
                        message.getBytes(StandardCharsets.UTF_8));

                previous = location;
                Thread.sleep(EVENT_DELAY_MS);
            }
        } catch (Exception e) {
            System.err.println("[CsvTripPublisher] Error publicando session=" + sessionId + ": " + e.getMessage());
            e.printStackTrace();
            markRetry(sessionId, "ERROR_PUBLICANDO");
        } finally {
            processingSessions.remove(sessionId);
        }
    }

    private Map<String, String> findTrip(String sessionId) throws Exception {
        return CsvUtil.readRows(dataDir, TRIPS_FILE).stream()
                .filter(row -> sessionId.equals(row.getOrDefault("sessionID", "")))
                .findFirst()
                .orElse(null);
    }

    private long findEmpresaId(long userId) throws Exception {
        return CsvUtil.readRows(dataDir, USERS_FILE).stream()
                .filter(row -> CsvUtil.parseLong(row.get("userID")) == userId)
                .map(row -> CsvUtil.parseLong(row.get("empresaID")))
                .findFirst()
                .orElse(0L);
    }

    private List<Map<String, String>> locationsFor(long userId, String sessionId, Map<String, String> trip) throws Exception {
        List<Map<String, String>> rows = CsvUtil.readRows(dataDir, LOCATIONS_FILE).stream()
                .filter(row -> CsvUtil.parseLong(row.get("userID")) == userId)
                .filter(row -> sessionId.equals(row.getOrDefault("sessionID", "")))
                .sorted(Comparator.comparing(row -> row.getOrDefault("timestamp", "")))
                .toList();
        if (!rows.isEmpty()) return rows;

        double startLat = CsvUtil.parseDouble(trip.get("origenLat"));
        double startLon = CsvUtil.parseDouble(trip.get("origenLon"));
        double endLat = CsvUtil.parseDouble(trip.get("destinoLat"));
        double endLon = CsvUtil.parseDouble(trip.get("destinoLon"));
        if (startLat == 0.0 || startLon == 0.0 || endLat == 0.0 || endLon == 0.0) return List.of();

        return List.of(
                Map.of("sessionID", sessionId, "userID", String.valueOf(userId), "timestamp", trip.getOrDefault("startTimestamp", ""), "latitud", String.valueOf(startLat), "longitud", String.valueOf(startLon), "speed", ""),
                Map.of("sessionID", sessionId, "userID", String.valueOf(userId), "timestamp", trip.getOrDefault("endTimestamp", ""), "latitud", String.valueOf(endLat), "longitud", String.valueOf(endLon), "speed", "")
        );
    }

    private void markRetry(String sessionId, String reason) {
        try {
            List<String> headers = CsvUtil.readHeaders(dataDir, TRIPS_FILE);
            List<Map<String, String>> trips = new ArrayList<>(CsvUtil.readRows(dataDir, TRIPS_FILE));
            for (Map<String, String> trip : trips) {
                if (sessionId.equals(trip.getOrDefault("sessionID", ""))) {
                    trip.put("estadoCalculo", "REINTENTAR_SYCD");
                    System.err.println("[CsvTripPublisher] " + reason + " session=" + sessionId);
                    break;
                }
            }
            CsvUtil.writeRows(dataDir, TRIPS_FILE, headers, trips);
        } catch (Exception ignored) {}
    }

    private double averageSpeedFromTrip(Map<String, String> trip) {
        double km = CsvUtil.parseDouble(trip.get("km"));
        double seconds = CsvUtil.parseDouble(trip.get("duracionSeg"));
        if (km > 0 && seconds > 0) return km / (seconds / 3600.0);
        return 4.5;
    }

    private long secondsBetween(String a, String b) {
        try {
            Instant first = Instant.parse(a);
            Instant second = Instant.parse(b);
            return Math.max(0, Duration.between(first, second).getSeconds());
        } catch (Exception e) {
            return 0;
        }
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double r = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void sleepQuietly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public static void main(String[] args) throws Exception {
        new CsvTripPublisher().runForever();
    }
}
