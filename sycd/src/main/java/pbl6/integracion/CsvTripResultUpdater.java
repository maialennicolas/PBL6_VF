package pbl6.integracion;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Actualiza data/viajes.csv para que la web vea el resultado calculado por SYCD. */
public final class CsvTripResultUpdater {
    private static final String TRIPS_FILE = "viajes.csv";
    private static final double BASELINE_CAR_KG_KM = 0.171;

    private CsvTripResultUpdater() {}

    public static synchronized void updateResult(int userId, String sessionId, String sycdMode,
                                                 String lat, String lon, String timestamp) {
        Path dataDir = CsvUtil.dataDir();
        try {
            List<String> headers = CsvUtil.readHeaders(dataDir, TRIPS_FILE);
            if (headers.isEmpty()) return;
            List<Map<String, String>> trips = new ArrayList<>(CsvUtil.readRows(dataDir, TRIPS_FILE));

            Map<String, String> target = null;
            for (Map<String, String> trip : trips) {
                boolean sameSession = sessionId != null && !sessionId.isBlank()
                        && sessionId.equals(trip.getOrDefault("sessionID", ""));
                boolean sameUserPending = CsvUtil.parseLong(trip.get("userID")) == userId
                        && !trip.getOrDefault("estadoCalculo", "").equalsIgnoreCase("CALCULADO");
                if (sameSession || (target == null && sameUserPending)) {
                    target = trip;
                    if (sameSession) break;
                }
            }

            if (target == null) {
                System.err.println("[CsvTripResultUpdater] No encuentro viaje para user=" + userId + " session=" + sessionId);
                return;
            }

            String webMode = normalizeMode(sycdMode);
            double km = CsvUtil.parseDouble(target.get("km"));
            if (km <= 0.0) km = estimateKm(target);
            double co2Saved = calculateSavedCo2Kg(km, webMode);
            int points = calculatePoints(km, co2Saved, webMode);

            target.put("km", formatOne(km));
            target.put("co2", formatOne(co2Saved));
            target.put("modo", webMode);
            target.put("puntos", String.valueOf(points));
            target.put("icono", iconForMode(webMode));
            target.put("estadoCalculo", "CALCULADO");
            if ((target.getOrDefault("destinoLat", "").isBlank() || CsvUtil.parseDouble(target.get("destinoLat")) == 0.0) && lat != null) {
                target.put("destinoLat", lat);
                target.put("destinoLon", lon == null ? "" : lon);
            }
            if (target.getOrDefault("endTimestamp", "").isBlank() && timestamp != null && !timestamp.isBlank()) {
                target.put("endTimestamp", normalizeTimestamp(timestamp));
            }

            CsvUtil.writeRows(dataDir, TRIPS_FILE, headers, trips);
            System.out.println(String.format(Locale.US,
                    "[CsvTripResultUpdater] Viaje actualizado user=%d session=%s modo=%s km=%.1f co2=%.1f puntos=%d",
                    userId, target.getOrDefault("sessionID", ""), webMode, km, co2Saved, points));
        } catch (Exception e) {
            System.err.println("[CsvTripResultUpdater] Error actualizando CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String normalizeMode(String mode) {
        if (mode == null) return "Besteak";
        return switch (mode.toUpperCase(Locale.ROOT)) {
            case "KOTXEA", "COCHE", "AUTOA" -> "Autoa";
            case "BUS", "AUTOBUSA" -> "Autobusa";
            case "TREN", "TRENA" -> "Trena";
            case "OINEZ", "KORRIKA" -> "Oinez";
            case "TXIRRINA", "BICI", "BICICLETA" -> "Bizikleta";
            case "PATIN", "PATINETE" -> "Patinete";
            default -> mode;
        };
    }

    private static String iconForMode(String mode) {
        return switch (mode) {
            case "Oinez" -> "🚶";
            case "Bizikleta" -> "🚲";
            case "Trena" -> "🚆";
            case "Autobusa" -> "🚌";
            case "Patinete" -> "🛴";
            case "Autoa" -> "🚘";
            default -> "🧭";
        };
    }

    private static double emissionFactor(String webMode) {
        return switch (webMode) {
            case "Autoa" -> BASELINE_CAR_KG_KM;
            case "Autobusa" -> 0.089;
            case "Trena" -> 0.035;
            case "Patinete" -> 0.020;
            case "Oinez", "Bizikleta" -> 0.0;
            default -> 0.050;
        };
    }

    private static double calculateSavedCo2Kg(double km, String mode) {
        return Math.max(0.0, km * (BASELINE_CAR_KG_KM - emissionFactor(mode)));
    }

    private static int calculatePoints(double km, double co2Saved, String mode) {
        if ("Autoa".equals(mode)) return 0;
        return Math.max(1, (int) Math.round(km * 5.0 + co2Saved * 20.0));
    }

    private static double estimateKm(Map<String, String> row) {
        double startLat = CsvUtil.parseDouble(row.get("origenLat"));
        double startLon = CsvUtil.parseDouble(row.get("origenLon"));
        double endLat = CsvUtil.parseDouble(row.get("destinoLat"));
        double endLon = CsvUtil.parseDouble(row.get("destinoLon"));
        if (startLat == 0.0 || startLon == 0.0 || endLat == 0.0 || endLon == 0.0) return 0.0;
        return haversineKm(startLat, startLon, endLat, endLon);
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static String formatOne(double value) {
        return String.format(Locale.US, "%.1f", Math.round(value * 10.0) / 10.0);
    }

    private static String normalizeTimestamp(String ts) {
        try {
            long millis = Long.parseLong(ts);
            return Instant.ofEpochMilli(millis).toString();
        } catch (Exception ignored) {}
        try {
            return LocalDate.parse(ts).atStartOfDay(ZoneId.systemDefault()).toInstant().toString();
        } catch (Exception ignored) {}
        return ts;
    }
}
