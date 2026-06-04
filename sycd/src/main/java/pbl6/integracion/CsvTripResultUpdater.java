package pbl6.integracion;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Actualiza data/viajes.csv para que la web vea el resultado calculado por SYCD. */
public final class CsvTripResultUpdater {
    private static final String TRIPS_FILE = "viajes.csv";
    private static final String USERS_FILE = "usuarios.csv";
    private static final String CARS_FILE = "coches.csv";
    private static final String JOINS_FILE = "carpool_uniones.csv";

    private static final double DEFAULT_CAR_KG_KM = 0.171;

    private static final List<String> EXTRA_TRIP_HEADERS = List.of(
            "co2ConsumidoKg",
            "co2AhorradoKg",
            "tripTypeIcon",
            "carpoolID",
            "esCarpool",
            "numPasajeros",
            "rolCarpool",
            "carpoolDriverSessionID");

    private CsvTripResultUpdater() {}

    public static synchronized void updateResult(int userId, String sessionId, String sycdMode,
                                                 String lat, String lon, String timestamp) {
        if (DbTripStore.isConfigured()) {
            try {
                if (updateResultDb(userId, sessionId, sycdMode, lat, lon, timestamp)) {
                    return;
                }
            } catch (Exception e) {
                System.err.println("[DbTripResultUpdater] Error actualizando BD, uso CSV de respaldo: " + e.getMessage());
                e.printStackTrace();
            }
        }
        updateResultCsv(userId, sessionId, sycdMode, lat, lon, timestamp);
    }

    private static void updateResultCsv(int userId, String sessionId, String sycdMode,
                                        String lat, String lon, String timestamp) {
        Path dataDir = CsvUtil.dataDir();
        try {
            List<String> headers = ensureHeaders(CsvUtil.readHeaders(dataDir, TRIPS_FILE));
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

            long carpoolId = CsvUtil.parseLong(target.get("carpoolID"));
            boolean isCarpool = Boolean.parseBoolean(target.getOrDefault("esCarpool", "false")) && carpoolId > 0;
            int peopleInCarpool = isCarpool
                    ? Math.max(CsvUtil.parseLong(target.get("numPasajeros")) > 0 ? (int) CsvUtil.parseLong(target.get("numPasajeros")) : 1,
                               countCarpoolPeople(dataDir, carpoolId))
                    : 1;

            CarInfo userCar = resolveUserCar(dataDir, userId);
            EmissionResult emission = calculateEmission(km, webMode, userCar, isCarpool, peopleInCarpool);

            applyTripResult(target, webMode, km, emission, isCarpool, peopleInCarpool, lat, lon, timestamp);

            if (isCarpool && "DRIVER".equalsIgnoreCase(target.getOrDefault("rolCarpool", "DRIVER"))) {
                upsertPassengerTrips(dataDir, headers, trips, target, carpoolId, peopleInCarpool);
            }

            CsvUtil.writeRows(dataDir, TRIPS_FILE, headers, trips);
            System.out.println(String.format(Locale.US,
                    "[CsvTripResultUpdater] Viaje actualizado user=%d session=%s modo=%s km=%.1f co2Consumido=%.1f co2Ahorrado=%.1f puntos=%d carpool=%s pasajeros=%d",
                    userId,
                    target.getOrDefault("sessionID", ""),
                    webMode,
                    km,
                    emission.consumedKg,
                    emission.savedKg,
                    emission.points,
                    isCarpool,
                    peopleInCarpool));
        } catch (Exception e) {
            System.err.println("[CsvTripResultUpdater] Error actualizando CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private static boolean updateResultDb(int userId, String sessionId, String sycdMode,
                                          String lat, String lon, String timestamp) throws Exception {
        List<String> headers = DbTripStore.TRIP_HEADERS;
        List<Map<String, String>> trips = new ArrayList<>(DbTripStore.readRows("viajes_ecomove", headers, "tripID"));
        if (trips.isEmpty()) {
            System.err.println("[DbTripResultUpdater] No hay viajes en BD todavía");
            return false;
        }

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
            System.err.println("[DbTripResultUpdater] No encuentro viaje en BD para user=" + userId + " session=" + sessionId);
            return false;
        }

        String webMode = normalizeMode(sycdMode);
        double km = CsvUtil.parseDouble(target.get("km"));
        if (km <= 0.0) km = estimateKm(target);

        long carpoolId = CsvUtil.parseLong(target.get("carpoolID"));
        boolean isCarpool = Boolean.parseBoolean(target.getOrDefault("esCarpool", "false")) && carpoolId > 0;
        int peopleInCarpool = isCarpool
                ? Math.max(CsvUtil.parseLong(target.get("numPasajeros")) > 0 ? (int) CsvUtil.parseLong(target.get("numPasajeros")) : 1,
                           DbTripStore.countCarpoolPeople(carpoolId))
                : 1;

        CarInfo userCar = DbTripStore.resolveUserCar(userId);
        EmissionResult emission = calculateEmission(km, webMode, userCar, isCarpool, peopleInCarpool);

        applyTripResult(target, webMode, km, emission, isCarpool, peopleInCarpool, lat, lon, timestamp);

        if (isCarpool && "DRIVER".equalsIgnoreCase(target.getOrDefault("rolCarpool", "DRIVER"))) {
            upsertPassengerTripsDb(headers, trips, target, carpoolId, peopleInCarpool);
        }

        DbTripStore.writeRows("viajes_ecomove", headers, trips);
        System.out.println(String.format(Locale.US,
                "[DbTripResultUpdater] Viaje actualizado en BD user=%d session=%s modo=%s km=%.1f co2Consumido=%.1f co2Ahorrado=%.1f puntos=%d carpool=%s pasajeros=%d",
                userId,
                target.getOrDefault("sessionID", ""),
                webMode,
                km,
                emission.consumedKg,
                emission.savedKg,
                emission.points,
                isCarpool,
                peopleInCarpool));
        return true;
    }

    private static void upsertPassengerTripsDb(List<String> headers,
                                               List<Map<String, String>> trips,
                                               Map<String, String> driverTrip,
                                               long carpoolId,
                                               int peopleInCarpool) throws Exception {
        String driverSessionId = driverTrip.getOrDefault("sessionID", "");
        long driverUserId = CsvUtil.parseLong(driverTrip.get("userID"));

        List<Long> passengerIds = DbTripStore.passengerIds(carpoolId).stream()
                .filter(id -> id > 0 && id != driverUserId)
                .distinct()
                .collect(Collectors.toList());

        long nextTripId = nextTripId(trips);
        for (long passengerId : passengerIds) {
            Map<String, String> passengerTrip = findPassengerTrip(trips, driverSessionId, passengerId, carpoolId);
            boolean isNew = passengerTrip == null;

            if (passengerTrip == null) {
                passengerTrip = new LinkedHashMap<>(driverTrip);
                passengerTrip.put("tripID", String.valueOf(nextTripId++));
                passengerTrip.put("sessionID", driverSessionId + "-P" + passengerId);
                trips.add(passengerTrip);
            }

            passengerTrip.put("userID", String.valueOf(passengerId));
            passengerTrip.put("carpoolID", String.valueOf(carpoolId));
            passengerTrip.put("esCarpool", "true");
            passengerTrip.put("numPasajeros", String.valueOf(Math.max(1, peopleInCarpool)));
            passengerTrip.put("rolCarpool", "PASSENGER");
            passengerTrip.put("carpoolDriverSessionID", driverSessionId);
            passengerTrip.put("tripTypeIcon", "👥");
            passengerTrip.put("estadoCalculo", "CALCULADO");

            for (String header : headers) {
                passengerTrip.putIfAbsent(header, "");
            }

            if (isNew) {
                System.out.println("[DbTripResultUpdater] Viaje carpool creado en BD para pasajero user=" + passengerId
                        + " desde session=" + driverSessionId);
            }
        }
    }

    private static List<String> ensureHeaders(List<String> existingHeaders) {
        if (existingHeaders == null || existingHeaders.isEmpty()) {
            return List.of();
        }

        List<String> headers = new ArrayList<>(existingHeaders);
        for (String header : EXTRA_TRIP_HEADERS) {
            if (!headers.contains(header)) {
                int index = preferredHeaderPosition(headers, header);
                if (index >= 0 && index <= headers.size()) {
                    headers.add(index, header);
                } else {
                    headers.add(header);
                }
            }
        }
        return headers;
    }

    private static int preferredHeaderPosition(List<String> headers, String header) {
        if ("co2ConsumidoKg".equals(header)) return after(headers, "co2");
        if ("co2AhorradoKg".equals(header)) return after(headers, "co2ConsumidoKg");
        if ("tripTypeIcon".equals(header)) return after(headers, "icono");
        return -1;
    }

    private static int after(List<String> headers, String existing) {
        int index = headers.indexOf(existing);
        return index >= 0 ? index + 1 : -1;
    }

    private static void applyTripResult(Map<String, String> target,
                                        String webMode,
                                        double km,
                                        EmissionResult emission,
                                        boolean isCarpool,
                                        int peopleInCarpool,
                                        String lat,
                                        String lon,
                                        String timestamp) {
        target.put("km", formatOne(km));
        target.put("co2", formatOne(emission.savedKg));
        target.put("co2ConsumidoKg", formatOne(emission.consumedKg));
        target.put("co2AhorradoKg", formatOne(emission.savedKg));
        target.put("modo", webMode);
        target.put("puntos", String.valueOf(emission.points));
        target.put("icono", iconForMode(webMode));
        target.put("tripTypeIcon", isCarpool ? "👥" : "👤");
        target.put("esCarpool", String.valueOf(isCarpool));
        target.put("numPasajeros", String.valueOf(Math.max(1, peopleInCarpool)));
        target.put("estadoCalculo", "CALCULADO");

        if (target.getOrDefault("rolCarpool", "").isBlank()) {
            target.put("rolCarpool", isCarpool ? "DRIVER" : "NONE");
        }

        if ((target.getOrDefault("destinoLat", "").isBlank() || CsvUtil.parseDouble(target.get("destinoLat")) == 0.0) && lat != null) {
            target.put("destinoLat", lat);
            target.put("destinoLon", lon == null ? "" : lon);
        }
        if (target.getOrDefault("endTimestamp", "").isBlank() && timestamp != null && !timestamp.isBlank()) {
            target.put("endTimestamp", normalizeTimestamp(timestamp));
        }
    }

    private static void upsertPassengerTrips(Path dataDir,
                                             List<String> headers,
                                             List<Map<String, String>> trips,
                                             Map<String, String> driverTrip,
                                             long carpoolId,
                                             int peopleInCarpool) throws Exception {
        String driverSessionId = driverTrip.getOrDefault("sessionID", "");
        long driverUserId = CsvUtil.parseLong(driverTrip.get("userID"));

        List<Long> passengerIds = CsvUtil.readRows(dataDir, JOINS_FILE).stream()
                .filter(row -> CsvUtil.parseLong(row.get("offerID")) == carpoolId)
                .filter(row -> row.getOrDefault("estado", "CONFIRMADO").equalsIgnoreCase("CONFIRMADO"))
                .map(row -> CsvUtil.parseLong(row.get("userID")))
                .filter(id -> id > 0 && id != driverUserId)
                .distinct()
                .collect(Collectors.toList());

        long nextTripId = nextTripId(trips);
        for (long passengerId : passengerIds) {
            Map<String, String> passengerTrip = findPassengerTrip(trips, driverSessionId, passengerId, carpoolId);
            boolean isNew = passengerTrip == null;

            if (passengerTrip == null) {
                passengerTrip = new LinkedHashMap<>(driverTrip);
                passengerTrip.put("tripID", String.valueOf(nextTripId++));
                passengerTrip.put("sessionID", driverSessionId + "-P" + passengerId);
                trips.add(passengerTrip);
            }

            passengerTrip.put("userID", String.valueOf(passengerId));
            passengerTrip.put("carpoolID", String.valueOf(carpoolId));
            passengerTrip.put("esCarpool", "true");
            passengerTrip.put("numPasajeros", String.valueOf(Math.max(1, peopleInCarpool)));
            passengerTrip.put("rolCarpool", "PASSENGER");
            passengerTrip.put("carpoolDriverSessionID", driverSessionId);
            passengerTrip.put("tripTypeIcon", "👥");
            passengerTrip.put("estadoCalculo", "CALCULADO");

            // Garantiza que cualquier columna nueva existe también en la fila copiada.
            for (String header : headers) {
                passengerTrip.putIfAbsent(header, "");
            }

            if (isNew) {
                System.out.println("[CsvTripResultUpdater] Viaje carpool creado para pasajero user=" + passengerId
                        + " desde session=" + driverSessionId);
            }
        }
    }

    private static Map<String, String> findPassengerTrip(List<Map<String, String>> trips,
                                                         String driverSessionId,
                                                         long passengerId,
                                                         long carpoolId) {
        for (Map<String, String> trip : trips) {
            boolean sameLinkedSession = driverSessionId.equals(trip.getOrDefault("carpoolDriverSessionID", ""));
            boolean samePassenger = CsvUtil.parseLong(trip.get("userID")) == passengerId;
            boolean sameCarpool = CsvUtil.parseLong(trip.get("carpoolID")) == carpoolId;
            boolean passengerRole = "PASSENGER".equalsIgnoreCase(trip.getOrDefault("rolCarpool", ""));
            if (sameLinkedSession && samePassenger && sameCarpool && passengerRole) {
                return trip;
            }
        }
        return null;
    }

    private static long nextTripId(List<Map<String, String>> trips) {
        long max = 0;
        for (Map<String, String> trip : trips) {
            max = Math.max(max, CsvUtil.parseLong(trip.get("tripID")));
        }
        return max + 1;
    }

    private static int countCarpoolPeople(Path dataDir, long carpoolId) {
        if (carpoolId <= 0) return 1;
        try {
            long passengers = CsvUtil.readRows(dataDir, JOINS_FILE).stream()
                    .filter(row -> CsvUtil.parseLong(row.get("offerID")) == carpoolId)
                    .filter(row -> row.getOrDefault("estado", "CONFIRMADO").equalsIgnoreCase("CONFIRMADO"))
                    .map(row -> CsvUtil.parseLong(row.get("userID")))
                    .filter(id -> id > 0)
                    .distinct()
                    .count();
            return Math.max(1, (int) passengers + 1);
        } catch (Exception e) {
            return 1;
        }
    }

    private static EmissionResult calculateEmission(double km,
                                                    String mode,
                                                    CarInfo userCar,
                                                    boolean isCarpool,
                                                    int peopleInCarpool) {
        double carKgKm = Math.max(0.0, userCar.kgKm);
        double savingsBaselineKgKm = carKgKm > 0.0 ? carKgKm : DEFAULT_CAR_KG_KM;

        if ("Autoa".equals(mode)) {
            if (isCarpool && peopleInCarpool >= 2) {
                double consumedPerPerson = (km * carKgKm) / peopleInCarpool;
                double comparableSoloTrip = km * savingsBaselineKgKm;
                double saved = Math.max(0.0, comparableSoloTrip - consumedPerPerson);
                int points = Math.max(1, (int) Math.round(saved * 5.0));
                return new EmissionResult(consumedPerPerson, saved, points);
            }

            double consumed = km * carKgKm;
            if (userCar.electric) {
                double saved = Math.max(0.0, km * (DEFAULT_CAR_KG_KM - carKgKm));
                int points = km > 0.0 ? Math.max(1, (int) Math.round(km / 5.0)) : 0;
                return new EmissionResult(consumed, saved, points);
            }

            return new EmissionResult(consumed, 0.0, 0);
        }

        double transportKgKm = emissionFactor(mode);
        double consumed = km * transportKgKm;
        double saved = Math.max(0.0, km * savingsBaselineKgKm - consumed);
        int points = calculateSustainablePoints(km, saved, mode);
        return new EmissionResult(consumed, saved, points);
    }

    private static int calculateSustainablePoints(double km, double co2Saved, String mode) {
        if (km <= 0.0 && co2Saved <= 0.0) {
            return 1;
        }

        double multiplier = switch (mode) {
            case "Oinez", "Bizikleta" -> 10.0;
            case "Patinete" -> 8.0;
            case "Trena" -> 7.0;
            case "Autobusa" -> 6.0;
            default -> 5.0;
        };

        return Math.max(1, (int) Math.round(km * 2.0 + co2Saved * multiplier));
    }

    private static CarInfo resolveUserCar(Path dataDir, int userId) {
        try {
            for (Map<String, String> user : CsvUtil.readRows(dataDir, USERS_FILE)) {
                if (CsvUtil.parseLong(user.get("userID")) == userId) {
                    boolean hasCar = Boolean.parseBoolean(user.getOrDefault("tieneCoche", "false"));
                    String modelId = user.getOrDefault("modeloCocheID", "SIN_COCHE");
                    if (!hasCar || modelId.isBlank() || "SIN_COCHE".equalsIgnoreCase(modelId)) {
                        return new CarInfo("SIN_COCHE", DEFAULT_CAR_KG_KM, "Referencia", false);
                    }
                    return resolveCarByModel(dataDir, modelId);
                }
            }
        } catch (Exception e) {
            System.err.println("[CsvTripResultUpdater] No se ha podido leer usuarios.csv: " + e.getMessage());
        }
        return new CarInfo("DEFAULT", DEFAULT_CAR_KG_KM, "Referencia", false);
    }

    private static CarInfo resolveCarByModel(Path dataDir, String modelId) {
        try {
            for (Map<String, String> car : CsvUtil.readRows(dataDir, CARS_FILE)) {
                if (car.getOrDefault("modeloCocheID", "").equalsIgnoreCase(modelId)) {
                    String type = car.getOrDefault("tipo", "");
                    double kgKm = Math.max(0.0, CsvUtil.parseDouble(car.get("emisionesKgKm")));
                    boolean electric = type.equalsIgnoreCase("Electrico")
                            || modelId.toUpperCase(Locale.ROOT).contains("EV")
                            || modelId.toUpperCase(Locale.ROOT).contains("TESLA")
                            || modelId.toUpperCase(Locale.ROOT).contains("LEAF");
                    return new CarInfo(modelId, kgKm, type, electric);
                }
            }
        } catch (Exception e) {
            System.err.println("[CsvTripResultUpdater] No se ha podido leer coches.csv: " + e.getMessage());
        }
        return new CarInfo(modelId, DEFAULT_CAR_KG_KM, "Referencia", false);
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
            case "Autoa" -> DEFAULT_CAR_KG_KM;
            case "Autobusa" -> 0.089;
            case "Trena" -> 0.035;
            case "Patinete" -> 0.020;
            case "Oinez", "Bizikleta" -> 0.0;
            default -> 0.050;
        };
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


    private static final class DbTripStore {
        private static final String DB_URL = env("DB_URL", "");
        private static final String DB_USER = env("DB_USER", "ecomove");
        private static final String DB_PASSWORD = env("DB_PASSWORD", "ecomove");

        private static final List<String> TRIP_HEADERS = List.of(
                "tripID", "userID", "fecha", "origen", "destino", "km", "co2", "co2ConsumidoKg",
                "co2AhorradoKg", "modo", "duracionMin", "puntos", "icono", "tripTypeIcon", "sessionID",
                "startTimestamp", "endTimestamp", "origenLat", "origenLon", "destinoLat", "destinoLon",
                "duracionSeg", "durationText", "estadoCalculo", "carpoolID", "esCarpool", "numPasajeros",
                "rolCarpool", "carpoolDriverSessionID");

        private static final List<String> JOIN_HEADERS = List.of(
                "joinID", "offerID", "userID", "riderName", "rol", "fecha", "estado");

        private DbTripStore() {}

        static boolean isConfigured() {
            return DB_URL != null && !DB_URL.isBlank();
        }

        static List<Map<String, String>> readRows(String table, List<String> headers, String orderColumn) throws Exception {
            ensureTable(table, headers);
            String sql = "SELECT " + joinColumns(headers) + " FROM " + q(table);
            if (orderColumn != null && !orderColumn.isBlank()) {
                sql += " ORDER BY CAST(" + q(orderColumn) + " AS UNSIGNED)";
            }

            try (Connection cn = connection();
                 Statement st = cn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                List<Map<String, String>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (String header : headers) {
                        String value = rs.getString(header);
                        row.put(header, value == null ? "" : value);
                    }
                    rows.add(row);
                }
                return rows;
            }
        }

        static void writeRows(String table, List<String> headers, List<Map<String, String>> rows) throws Exception {
            ensureTable(table, headers);
            try (Connection cn = connection(); Statement st = cn.createStatement()) {
                cn.setAutoCommit(false);
                try {
                    st.executeUpdate("DELETE FROM " + q(table));
                    String sql = "INSERT INTO " + q(table) + " (" + joinColumns(headers) + ") VALUES (" + placeholders(headers.size()) + ")";
                    try (PreparedStatement ps = cn.prepareStatement(sql)) {
                        for (Map<String, String> row : rows) {
                            for (int i = 0; i < headers.size(); i++) {
                                ps.setString(i + 1, row.getOrDefault(headers.get(i), ""));
                            }
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                    cn.commit();
                } catch (Exception e) {
                    cn.rollback();
                    throw e;
                } finally {
                    cn.setAutoCommit(true);
                }
            }
        }

        static int countCarpoolPeople(long carpoolId) throws Exception {
            if (carpoolId <= 0) return 1;
            ensureTable("carpool_uniones_ecomove", JOIN_HEADERS);
            String sql = "SELECT COUNT(DISTINCT " + q("userID") + ") FROM " + q("carpool_uniones_ecomove")
                    + " WHERE " + q("offerID") + " = ? AND UPPER(COALESCE(" + q("estado") + ", 'CONFIRMADO')) = 'CONFIRMADO'";
            try (Connection cn = connection(); PreparedStatement ps = cn.prepareStatement(sql)) {
                ps.setString(1, String.valueOf(carpoolId));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Math.max(1, rs.getInt(1) + 1);
                }
            }
            return 1;
        }

        static List<Long> passengerIds(long carpoolId) throws Exception {
            ensureTable("carpool_uniones_ecomove", JOIN_HEADERS);
            String sql = "SELECT DISTINCT " + q("userID") + " FROM " + q("carpool_uniones_ecomove")
                    + " WHERE " + q("offerID") + " = ? AND UPPER(COALESCE(" + q("estado") + ", 'CONFIRMADO')) = 'CONFIRMADO'";
            List<Long> ids = new ArrayList<>();
            try (Connection cn = connection(); PreparedStatement ps = cn.prepareStatement(sql)) {
                ps.setString(1, String.valueOf(carpoolId));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) ids.add(CsvUtil.parseLong(rs.getString(1)));
                }
            }
            return ids;
        }

        static CarInfo resolveUserCar(int userId) {
            try (Connection cn = connection();
                 PreparedStatement ps = cn.prepareStatement("SELECT " + q("tiene_coche") + ", " + q("modelo_cocheid")
                         + " FROM " + q("usuarios_ecomove") + " WHERE " + q("userid") + " = ?")) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        boolean hasCar = rs.getBoolean(1);
                        String modelId = rs.getString(2);
                        if (!hasCar || modelId == null || modelId.isBlank() || "SIN_COCHE".equalsIgnoreCase(modelId)) {
                            return new CarInfo("SIN_COCHE", DEFAULT_CAR_KG_KM, "Referencia", false);
                        }
                        return resolveCarByModel(modelId);
                    }
                }
            } catch (Exception e) {
                System.err.println("[DbTripResultUpdater] No se ha podido resolver el coche del usuario en BD: " + e.getMessage());
            }
            return new CarInfo("DEFAULT", DEFAULT_CAR_KG_KM, "Referencia", false);
        }

        private static CarInfo resolveCarByModel(String modelId) {
            try {
                ensureTable("coches_ecomove", List.of("modeloCocheID", "marca", "modelo", "tipo", "emisionesKgKm"));
                try (Connection cn = connection();
                     PreparedStatement ps = cn.prepareStatement("SELECT " + q("tipo") + ", " + q("emisionesKgKm")
                             + " FROM " + q("coches_ecomove") + " WHERE UPPER(" + q("modeloCocheID") + ") = UPPER(?)")) {
                    ps.setString(1, modelId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String type = rs.getString(1) == null ? "" : rs.getString(1);
                            double kgKm = Math.max(0.0, CsvUtil.parseDouble(rs.getString(2)));
                            boolean electric = type.equalsIgnoreCase("Electrico")
                                    || modelId.toUpperCase(Locale.ROOT).contains("EV")
                                    || modelId.toUpperCase(Locale.ROOT).contains("TESLA")
                                    || modelId.toUpperCase(Locale.ROOT).contains("LEAF");
                            return new CarInfo(modelId, kgKm, type, electric);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[DbTripResultUpdater] No se ha podido leer coches_ecomove: " + e.getMessage());
            }
            return new CarInfo(modelId, DEFAULT_CAR_KG_KM, "Referencia", false);
        }

        private static void ensureTable(String table, List<String> headers) throws Exception {
            try (Connection cn = connection(); Statement st = cn.createStatement()) {
                StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(q(table)).append(" (");
                for (int i = 0; i < headers.size(); i++) {
                    if (i > 0) sql.append(", ");
                    sql.append(q(headers.get(i))).append(" TEXT");
                }
                sql.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
                st.execute(sql.toString());

                for (String header : headers) {
                    if (!columnExists(cn, table, header)) {
                        st.execute("ALTER TABLE " + q(table) + " ADD COLUMN " + q(header) + " TEXT");
                    }
                }
            }
        }

        private static boolean columnExists(Connection cn, String table, String column) throws SQLException {
            String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
            try (PreparedStatement ps = cn.prepareStatement(sql)) {
                ps.setString(1, table);
                ps.setString(2, column);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        }

        private static Connection connection() throws SQLException {
            return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        }

        private static String joinColumns(List<String> headers) {
            return headers.stream().map(DbTripStore::q).reduce((a, b) -> a + ", " + b).orElse("");
        }

        private static String placeholders(int count) {
            return "?, ".repeat(Math.max(0, count)).replaceAll(", $", "");
        }

        private static String q(String identifier) {
            return "`" + identifier.replace("`", "``") + "`";
        }

        private static String env(String name, String fallback) {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? fallback : value;
        }
    }

    private record CarInfo(String modelId, double kgKm, String type, boolean electric) {}
    private record EmissionResult(double consumedKg, double savedKg, int points) {}
}
