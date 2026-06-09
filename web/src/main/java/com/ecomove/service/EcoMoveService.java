package com.ecomove.service;

import com.ecomove.model.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class EcoMoveService {

    private static final String TRIPS_FILE = "viajes.csv";
    private static final String MUNICIPALITIES_FILE = "municipios.csv";
    private static final double MAX_MUNICIPALITY_DISTANCE_KM = 20.0;
    private static final String REWARDS_FILE = "recompensas.csv";
    private static final String REDEMPTIONS_FILE = "canjeos.csv";
    private static final String LINES_FILE = "lineas_transporte.csv";
    private static final String STOPS_FILE = "paradas_transporte.csv";
    private static final String ROUTES_FILE = "rutas_recomendadas.csv";
    private static final String ROUTE_STEPS_FILE = "ruta_pasos.csv";
    private static final String OFFERS_FILE = "carpool_ofertas.csv";
    private static final String JOINS_FILE = "carpool_uniones.csv";
    private static final String LOCATIONS_FILE = "ubicaciones_bidaia.csv";

    private static final List<String> TRIP_HEADERS = List.of(
            "tripID",
            "userID",
            "fecha",
            "origen",
            "destino",
            "km",
            "co2",
            "co2ConsumidoKg",
            "co2AhorradoKg",
            "modo",
            "duracionMin",
            "puntos",
            "icono",
            "tripTypeIcon",
            "sessionID",
            "startTimestamp",
            "endTimestamp",
            "origenLat",
            "origenLon",
            "destinoLat",
            "destinoLon",
            "duracionSeg",
            "durationText",
            "estadoCalculo",
            "carpoolID",
            "esCarpool",
            "numPasajeros",
            "rolCarpool",
            "carpoolDriverSessionID");
    private static final List<String> REDEMPTION_HEADERS = List.of("redencionID", "userID", "rewardID", "fecha",
            "puntos");
    private static final List<String> OFFER_HEADERS = List.of("offerID", "userID", "origen", "destino", "time", "seats",
            "active", "distance", "rating");
    private static final List<String> LOCATION_HEADERS = List.of(
            "trackingID", "sessionID", "userID", "timestamp", "latitud", "longitud", "accuracy", "speed", "heading",
            "altitude");

    private static final List<String> JOIN_HEADERS = List.of(
            "joinID", "offerID", "userID", "riderName", "rol", "fecha", "estado");

    private final UserCsvService userCsvService;
    private final CsvDataService csv;
    private final RabbitMqTripPublisher sycdPublisher;

    public EcoMoveService(UserCsvService userCsvService, CsvDataService csv, RabbitMqTripPublisher sycdPublisher) {
        this.userCsvService = userCsvService;
        this.csv = csv;
        this.sycdPublisher = sycdPublisher;
    }

    public AuthResponse login(LoginRequest request) {
        Optional<User> userOptional = userCsvService.findByNombreUsuario(request.nombreUsuario());

        if (userOptional.isEmpty()) {
            return new AuthResponse(false, "Usuario no encontrado", null);
        }

        User user = userOptional.get();

        if (!user.contrasena().equals(request.contrasena())) {
            return new AuthResponse(false, "Contraseña incorrecta", null);
        }

        return new AuthResponse(true, "Login correcto", buildProfile(user));
    }

    public AuthResponse register(RegisterRequest request) {
        if (userCsvService.findByNombreUsuario(request.nombreUsuario()).isPresent()) {
            return new AuthResponse(false, "El nombre de usuario ya existe", null);
        }

        if (request.email() != null && !request.email().isBlank()
                && userCsvService.findByEmail(request.email()).isPresent()) {
            return new AuthResponse(false, "El email ya existe", null);
        }

        String modeloCoche = request.tieneCoche()
                ? safe(request.modeloCocheID(), "SIN_COCHE")
                : "SIN_COCHE";

        long newId = userCsvService.nextUserId();
        String email = request.email() == null || request.email().isBlank()
                ? request.nombreUsuario() + "@ecomove.local"
                : request.email();

        User user = new User(
                newId,
                request.empresaID(),
                request.nombre(),
                request.apellidos(),
                request.nombreUsuario(),
                request.contrasena(),
                email,
                request.tieneCoche(),
                modeloCoche,
                request.puebloCiudad());

        userCsvService.saveUser(user);

        return new AuthResponse(true, "Usuario registrado correctamente", buildProfile(user));
    }

    public UserProfile getProfile(long userId) {
        return buildProfile(getUser(userId));
    }

    public DashboardResponse getDashboard(long userId) {
        User user = getUser(userId);
        return new DashboardResponse(
                buildProfile(user),
                getStats(user.userID()),
                getMonthlyStats(user.userID()),
                getTransportShare(user.userID()),
                getRecentTrips(user.userID()),
                getRecommendedRoute(user.userID()));
    }

    public List<StatCard> getStats(long userId) {
        List<Map<String, String>> trips = userTripRows(userId);
        double co2 = trips.stream().mapToDouble(row -> parseDouble(row.get("co2"))).sum();
        double cleanKm = trips.stream()
                .filter(row -> !row.getOrDefault("modo", "").equalsIgnoreCase("Autoa")
                        || Boolean.parseBoolean(row.getOrDefault("esCarpool", "false")))
                .mapToDouble(row -> parseDouble(row.get("km"))).sum();
        int points = trips.stream().mapToInt(row -> parseInt(row.get("puntos"))).sum() - redeemedPoints(userId);
        int tripCount = trips.size();
        int level = Math.max(1, 1 + points / 250);

        return List.of(
                new StatCard("CO₂ Aurreztua", formatKg(co2), "", "🌳", "green"),
                new StatCard("Bidaiak", String.valueOf(tripCount), "", "🧭", "blue"),
                new StatCard("Nire Puntuak", formatNumber(points), "", "⭐", "yellow"),
                new StatCard("Km garbi", formatOne(cleanKm) + " km", "", "🚆", "purple"));
    }

    public List<MonthlyStat> getMonthlyStats(long userId) {
        int currentYear = LocalDate.now().getYear();

        Map<Integer, double[]> byMonth = new LinkedHashMap<>();

        for (int month = 1; month <= 12; month++) {
            byMonth.put(month, new double[] { 0.0, 0.0 });
        }

        for (Map<String, String> row : userTripRows(userId)) {
            try {
                LocalDate date = LocalDate.parse(row.getOrDefault("fecha", ""), DateTimeFormatter.ISO_LOCAL_DATE);

                if (date.getYear() != currentYear) {
                    continue;
                }

                double[] values = byMonth.get(date.getMonthValue());
                values[0] += parseDouble(row.get("co2"));
                values[1] += parseDouble(row.get("km"));
            } catch (Exception ignored) {
            }
        }

        return byMonth.entrySet().stream()
                .map(entry -> new MonthlyStat(
                        monthNameFromNumber(entry.getKey()),
                        roundOne(entry.getValue()[0]),
                        (int) Math.round(entry.getValue()[1])))
                .toList();
    }

    public List<TransportShare> getTransportShare(long userId) {
        List<Map<String, String>> trips = userTripRows(userId);

        if (trips.isEmpty()) {
            return List.of();
        }

        Map<String, Long> counts = trips.stream().collect(Collectors.groupingBy(
                row -> row.getOrDefault("modo", "Besteak"),
                LinkedHashMap::new,
                Collectors.counting()));

        int total = trips.size();
        return counts.entrySet().stream()
                .map(entry -> new TransportShare(entry.getKey(), (int) Math.round((entry.getValue() * 100.0) / total)))
                .toList();
    }

    public List<Trip> getRecentTrips(long userId) {
        return userTripRows(userId).stream()
                .sorted(Comparator.comparing((Map<String, String> row) -> row.getOrDefault("fecha", "")).reversed())
                .limit(8)
                .map(this::toTrip)
                .toList();
    }

    public RouteRecommendation getRecommendedRoute(long userId) {
        Optional<Map<String, String>> route = csv.readRows(ROUTES_FILE).stream()
                .filter(row -> parseLong(row.get("userID")) == userId)
                .findFirst();

        Map<String, String> row = route
                .orElseGet(() -> csv.readRows(ROUTES_FILE).stream().findFirst().orElse(Map.of()));
        long routeId = parseLong(row.get("routeID"));

        List<RouteStep> steps = csv.readRows(ROUTE_STEPS_FILE).stream()
                .filter(step -> parseLong(step.get("routeID")) == routeId)
                .sorted(Comparator.comparingInt(step -> parseInt(step.get("orden"))))
                .map(step -> new RouteStep(
                        step.getOrDefault("icon", "•"),
                        step.getOrDefault("label", "Pausoa"),
                        step.getOrDefault("detail", "")))
                .toList();

        return new RouteRecommendation(
                row.getOrDefault("origen", "Bilbo"),
                row.getOrDefault("destino", "Getxo"),
                row.getOrDefault("duracion", "0 min"),
                row.getOrDefault("distance", "0 km"),
                row.getOrDefault("co2", "0 kg"),
                steps);
    }

    public List<Rider> getRiders(long userId) {
        List<User> users = userCsvService.getAllUsers();

        return csv.readRows(OFFERS_FILE).stream()
                .filter(row -> Boolean.parseBoolean(row.getOrDefault("active", "false")))
                .filter(row -> parseLong(row.get("userID")) != userId)
                .map(row -> {
                    User driver = users.stream()
                            .filter(user -> user.userID() == parseLong(row.get("userID")))
                            .findFirst()
                            .orElse(null);

                    if (driver == null) {
                        return null;
                    }

                    String company = userCsvService.findCompany(driver.empresaID())
                            .map(Empresa::nombre)
                            .orElse("EcoMove");

                    String origin = row.getOrDefault("origen", "");
                    String destination = row.getOrDefault("destino", "");

                    return new Rider(
                            parseLong(row.get("offerID")),
                            driver.nombre() + " " + driver.apellidos(),
                            row.getOrDefault("distance", "0 km"),
                            parseDouble(row.get("rating")),
                            origin + " → " + destination,
                            row.getOrDefault("time", ""),
                            driver.modeloCocheID().toUpperCase().contains("TESLA")
                                    || driver.modeloCocheID().toUpperCase().contains("EV"),
                            getInitials(driver.nombre() + " " + driver.apellidos()),
                            company,
                            origin,
                            destination);
                })
                .filter(rider -> rider != null)
                .toList();
    }

    public List<TransportLine> getTransportLines() {
        return csv.readRows(LINES_FILE).stream().map(row -> new TransportLine(
                row.getOrDefault("id", ""),
                row.getOrDefault("name", ""),
                row.getOrDefault("color", "#16a34a"),
                parseInt(row.get("minutes")),
                row.getOrDefault("status", "garaiz"),
                parseInt(row.get("stops")))).toList();
    }

    public List<TransportStop> getTransportStops(String proveedor, Integer limit) {
        int max = limit == null || limit <= 0 ? 40 : Math.min(limit, 250);
        String filter = proveedor == null ? "" : proveedor.trim();

        return csv.readRows(STOPS_FILE).stream()
                .filter(row -> filter.isBlank() || row.getOrDefault("proveedor", "").equalsIgnoreCase(filter))
                .limit(max)
                .map(row -> new TransportStop(
                        row.getOrDefault("paradaID", ""),
                        row.getOrDefault("proveedor", ""),
                        row.getOrDefault("stopID", ""),
                        row.getOrDefault("stopCode", ""),
                        row.getOrDefault("nombre", ""),
                        row.getOrDefault("descripcion", ""),
                        parseDouble(row.get("latitud")),
                        parseDouble(row.get("longitud")),
                        row.getOrDefault("zona", ""),
                        row.getOrDefault("municipio", ""),
                        row.getOrDefault("locationType", ""),
                        row.getOrDefault("accesible", "")))
                .toList();
    }

    public List<Reward> getRewards(String category) {
        List<Reward> rewards = csv.readRows(REWARDS_FILE).stream().map(row -> new Reward(
                parseLong(row.get("rewardID")),
                row.getOrDefault("title", ""),
                parseInt(row.get("points")),
                row.getOrDefault("emoji", "🎁"),
                row.getOrDefault("category", ""))).toList();

        if (category == null || category.isBlank() || category.equalsIgnoreCase("Guztiak")) {
            return rewards;
        }

        return rewards.stream()
                .filter(reward -> reward.category().equalsIgnoreCase(category))
                .toList();
    }

    public TrackingStatus startTracking(LocationTrackRequest request) {
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? UUID.randomUUID().toString()
                : request.sessionId();

        LocationTrackRequest requestWithSession = new LocationTrackRequest(
                request.userId(),
                sessionId,
                request.latitude(),
                request.longitude(),
                request.accuracy(),
                request.speed(),
                request.heading(),
                request.altitude(),
                safe(request.timestamp(), Instant.now().toString()));

        appendLocation(requestWithSession);
        publishTrackingEvent(requestWithSession, false, requestWithSession.timestamp());
        return buildTrackingStatus(request.userId(), sessionId, true);
    }

    public TrackingStatus saveTrackingLocation(LocationTrackRequest request) {
        if (request.sessionId() == null || request.sessionId().isBlank()) {
            throw new IllegalArgumentException("sessionId es obligatorio para guardar una ubicación");
        }

        appendLocation(request);
        publishTrackingEvent(request, false, request.timestamp());
        return buildTrackingStatus(request.userId(), request.sessionId(), true);
    }

    public TrackingStatus stopTracking(long userId, String sessionId, long durationSeconds, String endTimestamp,
                                       long carpoolId, String rolCarpool) {
        String finalEndTimestamp = safe(endTimestamp, Instant.now().toString());

        List<Map<String, String>> locations = trackingRows(userId, sessionId);

        double km = calculateDistanceKm(locations);

        long finalDurationSeconds = durationSeconds > 0
                ? durationSeconds
                : calculateDurationSeconds(locations);

        int durationMin = finalDurationSeconds > 0
                ? (int) Math.max(1, Math.round(finalDurationSeconds / 60.0))
                : 0;

        String durationText = formatDurationSeconds(finalDurationSeconds);

        String startTimestamp = locations.isEmpty()
                ? ""
                : locations.get(0).getOrDefault("timestamp", "");

        Map<String, String> startLocation = firstTrackingLocation(locations);
        Map<String, String> endLocation = lastTrackingLocation(locations);

        double startLat = parseDouble(startLocation.get("latitud"));
        double startLon = parseDouble(startLocation.get("longitud"));
        double endLat = parseDouble(endLocation.get("latitud"));
        double endLon = parseDouble(endLocation.get("longitud"));

        String origen = placeNameFromCoordinates(startLat, startLon);
        String destino = placeNameFromCoordinates(endLat, endLon);

        long tripId = csv.nextId(TRIPS_FILE, "tripID");

        Map<String, String> carpoolOffer = carpoolId > 0 ? carpoolOfferById(carpoolId) : null;
        boolean isCarpool = carpoolOffer != null && parseLong(carpoolOffer.get("userID")) == userId;
        if (carpoolId > 0 && !isCarpool) {
            throw new IllegalStateException("Solo el conductor puede iniciar o cerrar un viaje carpool");
        }

        String normalizedRole = isCarpool ? "DRIVER" : "NONE";
        int passengerCount = isCarpool ? countCarpoolPeople(carpoolId) : 1;

        csv.appendRow(TRIPS_FILE, TRIP_HEADERS, List.of(
                String.valueOf(tripId),
                String.valueOf(userId),
                LocalDate.now().toString(),

                origen,
                destino,

                formatOne(km),
                "0.0",
                "0.0",
                "0.0",
                "SIN_CALCULAR",
                String.valueOf(durationMin),
                "0",
                "🧭",
                isCarpool ? "👥" : "👤",

                sessionId,
                startTimestamp,
                finalEndTimestamp,

                formatDouble(startLat, 6),
                formatDouble(startLon, 6),
                formatDouble(endLat, 6),
                formatDouble(endLon, 6),

                String.valueOf(finalDurationSeconds),
                durationText,
                "PROCESANDO_SYCD",
                isCarpool ? String.valueOf(carpoolId) : "",
                String.valueOf(isCarpool),
                String.valueOf(passengerCount),
                normalizedRole,
                ""));

        if (isCarpool) {
            deactivateCarpoolOffer(carpoolId);
        }

        Map<String, String> lastPoint = lastTrackingLocation(locations);
        boolean sentToSycd = publishTrackingEvent(locationRequestFromRow(userId, sessionId, lastPoint), true, finalEndTimestamp);
        if (!sentToSycd) {
            updateTripStatus(sessionId, "ERROR_ENVIO_SYCD");
        }

        return buildTrackingStatus(userId, sessionId, false);
    }


    private boolean publishTrackingEvent(LocationTrackRequest request, boolean finished, String eventTimestamp) {
        if (request == null || request.sessionId() == null || request.sessionId().isBlank()) {
            return false;
        }

        List<Map<String, String>> locations = trackingRows(request.userId(), request.sessionId());
        double meters = calculateDistanceKm(locations) * 1000.0;
        LocationTrackRequest enriched = request.speed() == null || request.speed() <= 0.0
                ? requestWithEstimatedSpeed(request, locations)
                : request;

        long empresaId = userCsvService.findById(request.userId())
                .map(User::empresaID)
                .orElse(0L);

        return sycdPublisher.publish(enriched, empresaId, meters, finished, eventTimestamp);
    }

    private LocationTrackRequest requestWithEstimatedSpeed(LocationTrackRequest request, List<Map<String, String>> locations) {
        double estimatedMps = estimateLastSpeedMps(locations);
        if (estimatedMps <= 0.0) {
            estimatedMps = estimateAverageSpeedMps(locations);
        }

        if (estimatedMps <= 0.0) {
            return request;
        }

        return new LocationTrackRequest(
                request.userId(),
                request.sessionId(),
                request.latitude(),
                request.longitude(),
                request.accuracy(),
                estimatedMps,
                request.heading(),
                request.altitude(),
                safe(request.timestamp(), Instant.now().toString()));
    }

    private double estimateLastSpeedMps(List<Map<String, String>> locations) {
        if (locations.size() < 2) {
            return 0.0;
        }

        Map<String, String> previous = locations.get(locations.size() - 2);
        Map<String, String> current = locations.get(locations.size() - 1);
        long seconds = secondsBetween(previous.get("timestamp"), current.get("timestamp"));
        if (seconds <= 0) {
            return 0.0;
        }

        double meters = haversineKm(
                parseDouble(previous.get("latitud")),
                parseDouble(previous.get("longitud")),
                parseDouble(current.get("latitud")),
                parseDouble(current.get("longitud"))) * 1000.0;
        return meters / seconds;
    }

    private double estimateAverageSpeedMps(List<Map<String, String>> locations) {
        long seconds = calculateDurationSeconds(locations);
        if (seconds <= 0) {
            return 0.0;
        }
        return (calculateDistanceKm(locations) * 1000.0) / seconds;
    }

    private long secondsBetween(String start, String end) {
        try {
            Instant first = parseInstant(start);
            Instant second = parseInstant(end);
            return Math.max(0, Duration.between(first, second).getSeconds());
        } catch (Exception e) {
            return 0;
        }
    }

    private LocationTrackRequest locationRequestFromRow(long userId, String sessionId, Map<String, String> row) {
        if (row == null || row.isEmpty()) {
            return new LocationTrackRequest(userId, sessionId, 0.0, 0.0, null, null, null, null, Instant.now().toString());
        }

        return new LocationTrackRequest(
                userId,
                sessionId,
                parseDouble(row.get("latitud")),
                parseDouble(row.get("longitud")),
                nullableDouble(row.get("accuracy")),
                nullableDouble(row.get("speed")),
                nullableDouble(row.get("heading")),
                nullableDouble(row.get("altitude")),
                safe(row.get("timestamp"), Instant.now().toString()));
    }

    private void updateTripStatus(String sessionId, String status) {
        List<Map<String, String>> trips = new ArrayList<>(csv.readRows(TRIPS_FILE));
        for (Map<String, String> trip : trips) {
            if (sessionId.equals(trip.getOrDefault("sessionID", ""))) {
                trip.put("estadoCalculo", status);
                break;
            }
        }
        csv.writeRows(TRIPS_FILE, TRIP_HEADERS, trips);
    }

    private void appendLocation(LocationTrackRequest request) {
        csv.appendRow(LOCATIONS_FILE, LOCATION_HEADERS, List.of(
                String.valueOf(csv.nextId(LOCATIONS_FILE, "trackingID")),
                safe(request.sessionId(), ""),
                String.valueOf(request.userId()),
                safe(request.timestamp(), Instant.now().toString()),
                formatDouble(request.latitude(), 6),
                formatDouble(request.longitude(), 6),
                formatNullable(request.accuracy()),
                formatNullable(request.speed()),
                formatNullable(request.heading()),
                formatNullable(request.altitude())));
    }

    private TrackingStatus buildTrackingStatus(long userId, String sessionId, boolean active) {
        List<Map<String, String>> locations = trackingRows(userId, sessionId);
        double km = calculateDistanceKm(locations);
        int durationMin = calculateDurationMinutes(locations);
        String lastTimestamp = locations.isEmpty() ? ""
                : locations.get(locations.size() - 1).getOrDefault("timestamp", "");

        return new TrackingStatus(
                active,
                "SIN_CALCULAR",
                formatOne(km) + " km",
                formatDuration(durationMin),
                "0.0 kg",
                0,
                sessionId,
                locations.size(),
                lastTimestamp);
    }

    private List<Map<String, String>> trackingRows(long userId, String sessionId) {
        return csv.readRows(LOCATIONS_FILE).stream()
                .filter(row -> parseLong(row.get("userID")) == userId)
                .filter(row -> row.getOrDefault("sessionID", "").equals(sessionId))
                .toList();
    }

    private double calculateDistanceKm(List<Map<String, String>> locations) {
        double total = 0.0;

        for (int i = 1; i < locations.size(); i++) {
            Map<String, String> previous = locations.get(i - 1);
            Map<String, String> current = locations.get(i);
            total += haversineKm(
                    parseDouble(previous.get("latitud")),
                    parseDouble(previous.get("longitud")),
                    parseDouble(current.get("latitud")),
                    parseDouble(current.get("longitud")));
        }

        return roundOne(total);
    }

    private int calculateDurationMinutes(List<Map<String, String>> locations) {
        if (locations.size() < 2) {
            return 0;
        }

        Instant first = parseInstant(locations.get(0).get("timestamp"));
        Instant last = parseInstant(locations.get(locations.size() - 1).get("timestamp"));
        long seconds = Math.max(0, Duration.between(first, last).getSeconds());
        return (int) Math.max(1, Math.round(seconds / 60.0));
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double earthRadiusKm = 6371.0;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return earthRadiusKm * c;
    }

    public long offerTrip(long userId, CarpoolOfferRequest request) {
        long offerId = csv.nextId(OFFERS_FILE, "offerID");
        csv.appendRow(OFFERS_FILE, OFFER_HEADERS, List.of(
                String.valueOf(offerId),
                String.valueOf(userId),
                safe(request.from(), ""),
                safe(request.to(), ""),
                safe(request.time(), "08:30"),
                String.valueOf(request.seats() <= 0 ? 1 : request.seats()),
                "true",
                "0.5 km",
                "4.7"));
        return offerId;
    }

    public void joinRide(long userId, long offerId, String riderName) {
        Map<String, String> offer = carpoolOfferById(offerId);
        if (offer == null || !Boolean.parseBoolean(offer.getOrDefault("active", "false"))) {
            throw new IllegalArgumentException("Karpool oferta ez da existitzen edo dagoeneko amaituta dago");
        }

        long driverId = parseLong(offer.get("userID"));
        if (driverId == userId) {
            throw new IllegalArgumentException("Gidaria ezin da bere karpoolera bidaiari gisa batu");
        }

        boolean alreadyJoined = csv.readRows(JOINS_FILE).stream()
                .filter(row -> parseLong(row.get("offerID")) == offerId)
                .filter(row -> parseLong(row.get("userID")) == userId)
                .anyMatch(row -> row.getOrDefault("estado", "CONFIRMADO").equalsIgnoreCase("CONFIRMADO"));
        if (alreadyJoined) {
            return;
        }

        String driverName = userCsvService.findById(driverId)
                .map(driver -> driver.nombre() + " " + driver.apellidos())
                .orElse(riderName);

        csv.appendRow(JOINS_FILE, JOIN_HEADERS, List.of(
                String.valueOf(csv.nextId(JOINS_FILE, "joinID")),
                String.valueOf(offerId),
                String.valueOf(userId),
                safe(driverName, ""),
                "PASSENGER",
                LocalDate.now().toString(),
                "CONFIRMADO"));
    }


    public List<Map<String, Object>> getMyCarpools(long userId) {
        List<User> users = userCsvService.getAllUsers();
        List<Map<String, String>> offers = csv.readRows(OFFERS_FILE);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, String> offer : offers) {
            long offerId = parseLong(offer.get("offerID"));
            long driverId = parseLong(offer.get("userID"));
            boolean active = Boolean.parseBoolean(offer.getOrDefault("active", "false"));
            if (active && driverId == userId) {
                result.add(carpoolSummary(offer, "DRIVER", users));
            }
        }

        for (Map<String, String> join : csv.readRows(JOINS_FILE)) {
            if (parseLong(join.get("userID")) != userId) {
                continue;
            }
            if (!join.getOrDefault("estado", "CONFIRMADO").equalsIgnoreCase("CONFIRMADO")) {
                continue;
            }

            long offerId = parseLong(join.get("offerID"));
            Map<String, String> offer = carpoolOfferById(offerId);
            if (offer == null || !Boolean.parseBoolean(offer.getOrDefault("active", "false"))) {
                continue;
            }
            result.add(carpoolSummary(offer, "PASSENGER", users));
        }

        return result.stream()
                .sorted(Comparator.comparing(item -> String.valueOf(item.getOrDefault("time", ""))))
                .toList();
    }

    private Map<String, Object> carpoolSummary(Map<String, String> offer, String role, List<User> users) {
        long offerId = parseLong(offer.get("offerID"));
        long driverId = parseLong(offer.get("userID"));
        User driver = users.stream()
                .filter(user -> user.userID() == driverId)
                .findFirst()
                .orElse(null);

        String driverName = driver == null
                ? "Gidaria"
                : driver.nombre() + " " + driver.apellidos();

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("offerId", offerId);
        item.put("driverId", driverId);
        item.put("driverName", driverName);
        item.put("from", offer.getOrDefault("origen", ""));
        item.put("to", offer.getOrDefault("destino", ""));
        item.put("time", offer.getOrDefault("time", ""));
        item.put("seats", parseInt(offer.get("seats")));
        item.put("passengers", countCarpoolPeople(offerId));
        item.put("role", role);
        item.put("status", offer.getOrDefault("active", "false").equalsIgnoreCase("true") ? "PENDIENTE" : "FINALIZADO");
        item.put("canStart", "DRIVER".equalsIgnoreCase(role));
        return item;
    }

    private void deactivateCarpoolOffer(long offerId) {
        if (offerId <= 0) {
            return;
        }

        List<Map<String, String>> offers = new ArrayList<>(csv.readRows(OFFERS_FILE));
        boolean changed = false;
        for (Map<String, String> offer : offers) {
            if (parseLong(offer.get("offerID")) == offerId) {
                offer.put("active", "false");
                changed = true;
            }
        }

        if (changed) {
            csv.writeRows(OFFERS_FILE, OFFER_HEADERS, offers);
        }
    }


    private Map<String, String> carpoolOfferById(long offerId) {
        if (offerId <= 0) {
            return null;
        }

        return csv.readRows(OFFERS_FILE).stream()
                .filter(row -> parseLong(row.get("offerID")) == offerId)
                .findFirst()
                .orElse(null);
    }

    private int countCarpoolPeople(long offerId) {
        if (offerId <= 0) {
            return 1;
        }

        long confirmedPassengers = csv.readRows(JOINS_FILE).stream()
                .filter(row -> parseLong(row.get("offerID")) == offerId)
                .filter(row -> row.getOrDefault("estado", "CONFIRMADO").equalsIgnoreCase("CONFIRMADO"))
                .map(row -> parseLong(row.get("userID")))
                .distinct()
                .count();

        return Math.max(1, (int) confirmedPassengers + 1);
    }

    public boolean redeemReward(long userId, long rewardId) {
        Optional<Reward> reward = getRewards(null).stream().filter(item -> item.id() == rewardId).findFirst();
        if (reward.isEmpty()) {
            return false;
        }

        int availablePoints = userTripRows(userId).stream().mapToInt(row -> parseInt(row.get("puntos"))).sum()
                - redeemedPoints(userId);
        if (availablePoints < reward.get().points()) {
            return false;
        }

        csv.appendRow(REDEMPTIONS_FILE, REDEMPTION_HEADERS, List.of(
                String.valueOf(csv.nextId(REDEMPTIONS_FILE, "redencionID")),
                String.valueOf(userId),
                String.valueOf(rewardId),
                LocalDate.now().toString(),
                String.valueOf(reward.get().points())));
        return true;
    }

    public CorporateDashboard getCorporateDashboard(long userId) {
        User currentUser = getUser(userId);
        long empresaID = currentUser.empresaID();
        int currentYear = LocalDate.now().getYear();

        List<User> companyUsers = userCsvService.getAllUsers().stream()
                .filter(user -> user.empresaID() == empresaID)
                .toList();

        List<Long> userIds = companyUsers.stream()
                .map(User::userID)
                .toList();

        List<Map<String, String>> companyTrips = csv.readRows(TRIPS_FILE).stream()
                .filter(row -> userIds.contains(parseLong(row.get("userID"))))
                .filter(row -> isTripFromYear(row, currentYear))
                .toList();

        double co2 = companyTrips.stream()
                .mapToDouble(row -> parseDouble(row.get("co2")))
                .sum();

        int points = companyTrips.stream()
                .mapToInt(row -> parseInt(row.get("puntos")))
                .sum();

        long autoTrips = companyTrips.stream()
                .filter(row -> row.getOrDefault("modo", "").equalsIgnoreCase("Autoa"))
                .count();

        int autoPercent = companyTrips.isEmpty()
                ? 0
                : (int) Math.round(autoTrips * 100.0 / companyTrips.size());

        List<CorporateKpi> kpis = List.of(
                new CorporateKpi("CO₂ Aurreztua", formatKg(co2), "Aurtengo datuak", "🌳", "green"),
                new CorporateKpi("Ibilaldi Aktiboak", String.valueOf(companyTrips.size()), "Urteko bidaiak", "👥",
                        "blue"),
                new CorporateKpi("Auto Erabilera", autoPercent + "%", "Autoz egindako bidaiak", "🚗", "yellow"),
                new CorporateKpi("Puntuak Irabazi", formatNumber(points), "Urteko guztira", "⭐", "purple"));

        Map<Integer, double[]> monthly = new LinkedHashMap<>();
        Map<Integer, List<Long>> activeEmployeesByMonth = new LinkedHashMap<>();

        for (int month = 1; month <= 12; month++) {
            monthly.put(month, new double[] { 0.0 });
            activeEmployeesByMonth.put(month, new ArrayList<>());
        }

        for (Map<String, String> row : companyTrips) {
            LocalDate date = parseCsvDate(row.get("fecha"));

            if (date == null) {
                continue;
            }

            int month = date.getMonthValue();

            monthly.get(month)[0] += parseDouble(row.get("co2"));

            long tripUserId = parseLong(row.get("userID"));
            List<Long> activeUsers = activeEmployeesByMonth.get(month);

            if (!activeUsers.contains(tripUserId)) {
                activeUsers.add(tripUserId);
            }
        }

        List<CorporateMonthlyStat> monthlyStats = monthly.entrySet().stream()
                .map(entry -> new CorporateMonthlyStat(
                        monthNameFromNumber(entry.getKey()),
                        (int) Math.round(entry.getValue()[0]),
                        activeEmployeesByMonth.get(entry.getKey()).size()))
                .toList();

        List<Employee> topEmployees = companyUsers.stream()
                .map(user -> {
                    List<Map<String, String>> trips = userTripRows(user.userID()).stream()
                            .filter(row -> isTripFromYear(row, currentYear))
                            .toList();

                    int tripCount = trips.size();

                    int userPoints = trips.stream()
                            .mapToInt(row -> parseInt(row.get("puntos")))
                            .sum();

                    double userCo2 = trips.stream()
                            .mapToDouble(row -> parseDouble(row.get("co2")))
                            .sum();

                    return new Employee(
                            0,
                            user.nombre() + " " + user.apellidos(),
                            getInitials(user.nombre() + " " + user.apellidos()),
                            user.puebloCiudad(),
                            tripCount,
                            formatKg(userCo2),
                            userPoints);
                })
                .sorted(Comparator.comparingInt(Employee::points).reversed())
                .toList();

        List<Employee> ranked = new ArrayList<>();

        for (int i = 0; i < topEmployees.size(); i++) {
            Employee e = topEmployees.get(i);

            ranked.add(new Employee(
                    i + 1,
                    e.name(),
                    e.initials(),
                    e.department(),
                    e.trips(),
                    e.co2Saved(),
                    e.points()));
        }

        Map<String, Long> byCity = companyUsers.stream()
                .collect(Collectors.groupingBy(
                        User::puebloCiudad,
                        LinkedHashMap::new,
                        Collectors.counting()));

        List<DepartmentParticipation> departments = byCity.entrySet().stream()
                .map(entry -> new DepartmentParticipation(
                        entry.getKey(),
                        companyUsers.isEmpty()
                                ? 0
                                : (int) Math.round(entry.getValue() * 100.0 / companyUsers.size()),
                        entry.getValue().intValue()))
                .toList();

        return new CorporateDashboard(kpis, monthlyStats, ranked, departments);
    }

    public List<Empresa> getCompanies() {
        return userCsvService.getCompanies();
    }

    public List<CarModel> getCarModels() {
        return userCsvService.getCarModels();
    }

    public String exportCsv(String filename) {
        return csv.readRaw(filename);
    }

    public String dataDirectory() {
        return csv.getDataDir().toString();
    }

    private UserProfile buildProfile(User user) {
        String organization = userCsvService.findCompany(user.empresaID()).map(Empresa::nombre).orElse("EcoMove");
        List<Map<String, String>> trips = userTripRows(user.userID());
        int totalPoints = trips.stream().mapToInt(row -> parseInt(row.get("puntos"))).sum()
                - redeemedPoints(user.userID());
        double co2 = trips.stream().mapToDouble(row -> parseDouble(row.get("co2"))).sum();
        int level = Math.max(1, 1 + totalPoints / 250);

        return new UserProfile(
                user.userID(),
                user.nombre() + " " + user.apellidos(),
                getInitials(user.nombre() + " " + user.apellidos()),
                user.email(),
                organization,
                user.puebloCiudad(),
                level,
                totalPoints,
                trips.size(),
                formatKg(co2),
                badgeForPoints(totalPoints),
                user.empresaID(),
                user.nombreUsuario(),
                user.tieneCoche(),
                user.modeloCocheID(),
                user.puebloCiudad());
    }

    private User getUser(long userId) {
        return userCsvService.findById(userId)
                .orElseGet(() -> userCsvService.getAllUsers().stream().findFirst()
                        .orElseThrow(() -> new IllegalStateException("No hay usuarios en data/usuarios.csv")));
    }

    private List<Map<String, String>> userTripRows(long userId) {
        return csv.readRows(TRIPS_FILE).stream()
                .filter(row -> parseLong(row.get("userID")) == userId)
                .toList();
    }

    private Trip toTrip(Map<String, String> row) {
        String mode = row.getOrDefault("modo", "SIN_CALCULAR");
        String status = row.getOrDefault("estadoCalculo", "CALCULADO");

        boolean isCarpool = Boolean.parseBoolean(row.getOrDefault("esCarpool", "false"));
        int passengers = Math.max(1, parseInt(row.get("numPasajeros")));
        String tripTypeIcon = row.getOrDefault("tripTypeIcon", isCarpool ? "👥" : "👤");
        double co2Saved = parseDouble(row.getOrDefault("co2AhorradoKg", row.get("co2")));
        double co2Consumed = parseDouble(row.getOrDefault("co2ConsumidoKg", "0"));

        return new Trip(
                parseLong(row.get("tripID")),
                row.getOrDefault("sessionID", ""),
                row.getOrDefault("origen", ""),
                row.getOrDefault("destino", ""),
                formatOne(parseDouble(row.get("km"))) + " km",
                formatOne(parseDouble(row.get("co2"))) + " kg",
                mode,
                durationTextFromRow(row),
                row.getOrDefault("fecha", ""),
                row.getOrDefault("icono", iconForMode(mode)),
                "+" + parseInt(row.get("puntos")) + " pts",
                status,
                tripTypeIcon,
                isCarpool,
                row.getOrDefault("carpoolID", ""),
                passengers,
                row.getOrDefault("rolCarpool", "NONE"),
                formatOne(co2Consumed) + " kg",
                formatOne(co2Saved) + " kg");
    }

    private int redeemedPoints(long userId) {
        return csv.readRows(REDEMPTIONS_FILE).stream()
                .filter(row -> parseLong(row.get("userID")) == userId)
                .mapToInt(row -> parseInt(row.get("puntos")))
                .sum();
    }

    private String badgeForPoints(int points) {
        if (points >= 1000)
            return "Ekologista Aurreratua";
        if (points >= 500)
            return "Bidaiari Berdea";
        if (points >= 100)
            return "Ekologista";
        return "Hasiberria";
    }

    private long calculateDurationSeconds(List<Map<String, String>> locations) {
        if (locations.size() < 2) {
            return 0;
        }

        Instant first = parseInstant(locations.get(0).get("timestamp"));
        Instant last = parseInstant(locations.get(locations.size() - 1).get("timestamp"));

        return Math.max(0, Duration.between(first, last).getSeconds());
    }

    private String getInitials(String name) {
        String[] parts = name == null ? new String[0] : name.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isBlank()) {
            return "EM";
        }
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
    }

    private String iconForMode(String mode) {
        return switch (mode == null ? "" : mode) {
            case "Oinez" -> "🚶";
            case "Bizikleta" -> "🚲";
            case "Metroa" -> "🚇";
            case "Tranbaia" -> "🚋";
            case "Karpoola" -> "🚗";
            case "Autoa" -> "🚘";
            default -> "🚌";
        };
    }

    private String monthName(String dateValue) {
        try {
            int month = LocalDate.parse(dateValue, DateTimeFormatter.ISO_LOCAL_DATE).getMonthValue();
            return switch (month) {
                case 1 -> "Urt";
                case 2 -> "Ots";
                case 3 -> "Mar";
                case 4 -> "Api";
                case 5 -> "Mai";
                case 6 -> "Eka";
                case 7 -> "Uzt";
                case 8 -> "Abu";
                case 9 -> "Ira";
                case 10 -> "Urr";
                case 11 -> "Aza";
                case 12 -> "Abe";
                default -> "?";
            };
        } catch (Exception e) {
            return "?";
        }
    }

    private String formatKg(double value) {
        return formatOne(value) + " kg";
    }

    private String formatOne(double value) {
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    private String formatNumber(int value) {
        return String.format(java.util.Locale.GERMANY, "%d", value);
    }

    private double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value == null || value.isBlank() ? "0" : value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value == null || value.isBlank() ? "0" : value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value == null || value.isBlank() ? "0" : value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private Map<String, String> firstTrackingLocation(List<Map<String, String>> locations) {
        if (locations == null || locations.isEmpty()) {
            return Map.of();
        }

        return locations.stream()
                .filter(row -> row.getOrDefault("eventType", "").equalsIgnoreCase("START"))
                .findFirst()
                .orElse(locations.get(0));
    }

    private Map<String, String> lastTrackingLocation(List<Map<String, String>> locations) {
        if (locations == null || locations.isEmpty()) {
            return Map.of();
        }

        return locations.stream()
                .filter(row -> row.getOrDefault("eventType", "").equalsIgnoreCase("END"))
                .reduce((first, second) -> second)
                .orElse(locations.get(locations.size() - 1));
    }

    private String placeNameFromCoordinates(double lat, double lon) {
        List<Map<String, String>> municipalities = csv.readRows(MUNICIPALITIES_FILE);

        if (municipalities.isEmpty() || lat == 0.0 || lon == 0.0) {
            return "Pendiente de calcular";
        }

        String bestName = "Pendiente de calcular";
        double bestDistance = Double.MAX_VALUE;

        for (Map<String, String> row : municipalities) {
            double townLat = parseDouble(row.get("latitud"));
            double townLon = parseDouble(row.get("longitud"));

            double distance = haversineKm(lat, lon, townLat, townLon);

            if (distance < bestDistance) {
                bestDistance = distance;
                bestName = row.getOrDefault("municipio", "Pendiente de calcular");
            }
        }

        if (bestDistance > MAX_MUNICIPALITY_DISTANCE_KM) {
            return "Pendiente de calcular";
        }

        return cleanMunicipalityName(bestName);
    }

    private String cleanMunicipalityName(String name) {
        if (name == null || name.isBlank()) {
            return "Pendiente de calcular";
        }

        return switch (name) {
            case "Arrasate/Mondragón" -> "Arrasate";
            case "Donostia-San Sebastián" -> "Donostia";
            case "Soraluze/Placencia de las Armas" -> "Soraluze";
            case "Urduña/Orduña" -> "Urduña";
            case "Karrantza Harana/Valle de Carranza" -> "Karrantza";
            default -> name;
        };
    }

    private String formatDurationSeconds(long totalSeconds) {
        totalSeconds = Math.max(0, totalSeconds);

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        return String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private String durationTextFromRow(Map<String, String> row) {
        String durationText = row.getOrDefault("durationText", "");

        if (!durationText.isBlank()) {
            return durationText;
        }

        long seconds = parseLong(row.get("duracionSeg"));

        if (seconds > 0) {
            return formatDurationSeconds(seconds);
        }

        int minutes = parseInt(row.get("duracionMin"));

        if (minutes > 0) {
            return minutes + " min";
        }

        return "00:00:00";
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String formatDuration(int minutes) {
        if (minutes <= 0) {
            return "00:00";
        }

        int hours = minutes / 60;
        int mins = minutes % 60;
        return String.format(java.util.Locale.US, "%02d:%02d", hours, mins);
    }

    private String formatNullable(Double value) {
        return value == null ? "" : formatDouble(value, 3);
    }

    private Double nullableDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatDouble(double value, int decimals) {
        return String.format(java.util.Locale.US, "%." + decimals + "f", value);
    }

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value == null || value.isBlank() ? Instant.now().toString() : value);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    public UserProfile updateProfile(ProfileUpdateRequest request) {
        User updatedUser = userCsvService.updateProfile(request);
        return buildProfile(updatedUser);
    }

    private boolean isTripFromYear(Map<String, String> row, int year) {
    LocalDate date = parseCsvDate(row.get("fecha"));
    return date != null && date.getYear() == year;
}

private LocalDate parseCsvDate(String value) {
    try {
        if (value == null || value.isBlank()) {
            return null;
        }

        return LocalDate.parse(value);
    } catch (Exception e) {
        return null;
    }
}

private String monthNameFromNumber(int month) {
    return switch (month) {
        case 1 -> "Urtarrila";
        case 2 -> "Otsaila";
        case 3 -> "Martxoa";
        case 4 -> "Apirila";
        case 5 -> "Maiatza";
        case 6 -> "Ekaina";
        case 7 -> "Uztaila";
        case 8 -> "Abuztua";
        case 9 -> "Iraila";
        case 10 -> "Urria";
        case 11 -> "Azaroa";
        case 12 -> "Abendua";
        default -> "?";
    };
}

}
